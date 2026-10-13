package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.ScrollResult;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.FEED_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service

public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {
    @Resource
    private IUserService userService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private IFollowService followService;

    // ===== 新增：点赞 Lua 脚本（判断+写Redis 原子化，防止并发重复点赞）=====
    private static final DefaultRedisScript<Long> LIKE_SCRIPT;
    static {
        LIKE_SCRIPT = new DefaultRedisScript<>();
        LIKE_SCRIPT.setLocation(new ClassPathResource("likeBlog.lua"));
        LIKE_SCRIPT.setResultType(Long.class);
    }


    @Override
    public Result queryHotBlog(Integer current) {
        Page<Blog> page =query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog -> {
            this.queryBlogUser(blog);
            // 查询点赞状态
            this.isBlogLike(blog);
        });
        return Result.ok(records);
    }


    @Override
    public Result queryBlogById(Long id) {
        //1.查询blog
        Blog blog = getById(id);
        //2.查询blog有关的用户
        if(blog==null) {
            return Result.fail("笔记不存在");
        }

//3.查询blog是否被点赞过
        queryBlogUser(blog);
        isBlogLike(blog);
        return Result.ok(blog);
    }

    private void isBlogLike(Blog blog) {
        // ===== 原实现1（已注释）：未登录时 UserHolder.getUser() 为 null，直接 NPE =====
        ////1.获取登录用户
        //Long userId = UserHolder.getUser().getId();
        ////2.判断当前用户是否已经点赞
        //String key="blog:liked:"+blog.getId();
        //Boolean isMember = stringRedisTemplate.opsForSet().isMember(key, userId.toString());
        //blog.setIsLike(BooleanUtil.isTrue(isMember));

        // ===== 原实现2（已注释）：Set 版判空修复 =====
        ////1.获取登录用户（可能为 null：/blog/hot 是放行接口，未登录也能访问）
        //UserDTO user = UserHolder.getUser();
        //if (user == null) {
        //    blog.setIsLike(false);
        //    return;
        //}
        ////2.判断当前用户是否已经点赞
        //String key = RedisConstants.BLOG_LIKED_KEY + blog.getId();
        //Boolean isMember = stringRedisTemplate.opsForSet().isMember(key, user.getId().toString());
        //blog.setIsLike(BooleanUtil.isTrue(isMember));

        // ===== 新实现：ZSet 版（点赞集合改为 ZSet，用 score 是否存在判断）=====
        //1.获取登录用户（可能为 null：/blog/hot 是放行接口，未登录也能访问）
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            blog.setIsLike(false);
            return;
        }
        //2.判断当前用户是否已经点赞：ZScore 有值即点过赞
        String key = RedisConstants.BLOG_LIKED_KEY + blog.getId();
        Double score = stringRedisTemplate.opsForZSet().score(key, user.getId().toString());
        blog.setIsLike(score != null);
    }


    @Override
    public Result likeBlog(Long id) {
        // ===== 原实现1（已注释）：判断/更新DB/写Redis 三步非原子，连点或并发时 DB 重复 +1，Redis set 去重拦不住 =====
        //String key="blog:liked:"+id;
        ////1.获取登录用户
        //Long userId = UserHolder.getUser().getId();
        ////2.判断当前用户是否已经点赞
        //Boolean isMember = stringRedisTemplate.opsForSet().isMember(key, userId.toString());
        //if (BooleanUtil.isFalse(isMember)) {
        //      //3.如果未点赞，可以点赞
        //      //3.1 数据库点赞数量+1
        //    boolean isSuccess = update().setSql("liked=liked+1").eq("id", id).update();
        //    //3.2 更新blog，保存用户到redis的set集合
        //    if (isSuccess) {
        //        stringRedisTemplate.opsForSet().add(key, userId.toString());
        //    }
        //  }else {
        //    //4.如果已点赞，取消点赞
        //    //4.1 点赞数量-1
        //    boolean isSuccess = update().setSql("liked=liked-1").eq("id", id).update();
        //    //4.2 更新blog把用户从redis的set集合中移除
        //    if (isSuccess) {
        //        stringRedisTemplate.opsForSet().remove(key, userId.toString());
        //    }
        //    //4.3 返回新的点赞数量
        //}
        ////4.返回结果
        //return Result.ok();

        // ===== 原实现2（已注释）：Lua 脚本原子化（Set 版）=====
        ////1.获取登录用户
        //Long userId = UserHolder.getUser().getId();
        ////2.原子执行：返回 1=点赞成功，0=取消点赞
        //String key = RedisConstants.BLOG_LIKED_KEY + id;
        //Long result = stringRedisTemplate.execute(
        //        LIKE_SCRIPT,
        //        Collections.singletonList(key),
        //        userId.toString());
        ////3.根据脚本结果更新数据库点赞数（result 为 null 时不更新）
        //if (result != null) {
        //    update().setSql(result == 1L ? "liked=liked+1" : "liked=liked-1")
        //            .eq("id", id).update();
        //}
        ////4.返回结果
        //return Result.ok();

        // ===== 新实现：Lua 脚本原子执行（ZSet 版，score=点赞时间戳，支持点赞排行榜）=====
        //1.获取登录用户
        Long userId = UserHolder.getUser().getId();
        //2.原子执行：返回 1=点赞成功，0=取消点赞；score 用当前毫秒时间戳
        String key = RedisConstants.BLOG_LIKED_KEY + id;
        Long result = stringRedisTemplate.execute(
                LIKE_SCRIPT,
                Collections.singletonList(key),
                userId.toString(),
                String.valueOf(System.currentTimeMillis()));
        //3.根据脚本结果更新数据库点赞数（result 为 null 时不更新）
        if (result != null) {
            update().setSql(result == 1L ? "liked=liked+1" : "liked=liked-1")
                    .eq("id", id).update();
        }
        //4.返回结果
        return Result.ok();
    }

    @Override
    public Result queryBlogLikes(Long id) {
        //1.查询 ZSet 中点赞时间最新的前5名用户 id（score 越大=点赞时间越晚，倒序取前5）
        String key = RedisConstants.BLOG_LIKED_KEY + id;
        Set<String> top5 = stringRedisTemplate.opsForZSet().range(key, 0, 4);
        if (top5 == null || top5.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        //2.解析用户 id
        List<Long> ids = top5.stream().map(Long::valueOf).collect(Collectors.toList());
        //3.按 ids 顺序查用户（FIELD 保证返回顺序与点赞时间一致）
        String idStr = StrUtil.join(",", ids);
        List<UserDTO> userDTOS = userService.query()
                .in("id", ids)
                .last("ORDER BY FIELD(id," + idStr + ")")
                .list()
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        //4.返回
        return Result.ok(userDTOS);
    }

    @Override
    public Result saveBlog(Blog blog) {
        // 获取登录用户
        UserDTO user= UserHolder.getUser();
        blog.setUserId(user.getId());
        // 保存探店博文
        boolean isSuccess = save(blog);
        if (!isSuccess) {
            return Result.fail("新增失败");
        }
//查询笔记作者的所有粉丝select * from tb_follow where follow_user_id = ?
        //推送笔记id给所有粉丝
        List<Follow> follows = followService.query().eq("follow_user_id", user.getId()).list();
        for (Follow follow : follows) {

            Long userId = follow.getUserId();
            //推送
            String key = "feed:" + userId;
            stringRedisTemplate.opsForZSet().add(key, blog.getId().toString(), System.currentTimeMillis());

        }

           

        //推送笔记id给所有粉丝

        // 返回id
        return Result.ok(blog.getId());

    }

    @Override
    public Result queryBlogOfFollow(Long max, Integer offset) {
        //1.获取当前用户
        Long userId = UserHolder.getUser().getId();
        //2.查询收件箱 ZREVRANGEBYSCORE key Max Min LIMIT offset count
        String key =FEED_KEY  + userId;
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet()
                .reverseRangeByScoreWithScores(key, 0, max, offset, 3);
        //非空判断
        if(typedTuples == null || typedTuples.isEmpty()) {
            return Result.ok();
        }
        //3。解析数据：blogId、minTime(时间戳)、offset
        List<Long> ids=new ArrayList<>(typedTuples.size());
        long minTime=0;
        int os=1;
        for(ZSetOperations.TypedTuple<String> tuple : typedTuples) {
            //获取id
            ids.add(Long.valueOf( tuple.getValue()));
            //获取分数（时间戳）
            long time = tuple.getScore().longValue();
            if (time==minTime) {
                os++;
            }
                else {
                minTime = time;
                os=1;
            }


        }

        //4.根据id查询blog
        String idStr=StrUtil.join(",",ids);
        List<Blog> blogs =query().in("id",ids).last("ORDER BY FIELD(id,"+idStr+")").list();
        for (Blog blog : blogs) {
            //4.1查询blog有关的用户
            queryBlogUser(blog);
            //4.2.查询blog是否被点赞过
            isBlogLike(blog);
        }
        //5.封装返回
        ScrollResult r=new ScrollResult();
        r.setList(blogs);
        r.setOffset(os);
        r.setMinTime(minTime);
        return Result.ok(r);
    }

    private void queryBlogUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }
}
