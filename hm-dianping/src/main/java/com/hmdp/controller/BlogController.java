package com.hmdp.controller;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.service.IBlogService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/blog")
public class BlogController {

    @Resource
    private IBlogService blogService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;


    @PostMapping
    public Result saveBlog(@RequestBody Blog blog) {
      return blogService.saveBlog(blog);
    }

    @PutMapping("/like/{id}")
    public Result likeBlog(@PathVariable("id") Long id) {

        return blogService.likeBlog(id);
    }

    @GetMapping("/likes/{id}")
    public Result queryBlogLikes(@PathVariable("id") Long id) {

        return blogService.queryBlogLikes(id);
    }

    @GetMapping("/of/me")
    public Result queryMyBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        // 根据用户查询
        Page<Blog> page = blogService.query()
                .eq("user_id", user.getId()).page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        return Result.ok(records);
    }

    @GetMapping("/hot")
    public Result queryHotBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        // 根据用户查询
        return blogService.queryHotBlog(current);
    }

    @GetMapping("/{id}")
    public Result queryBlogById(@PathVariable("id") Long id) {
        return blogService.queryBlogById(id);
    }
    @GetMapping("/of/user")
    public Result queryUserByUserId(
            @RequestParam(value="current",defaultValue = "1") Integer current,
            @RequestParam("id") Long id){
        Page<Blog> page=blogService.query()
                .eq("user_id",id)
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        //获取当前页数据
        List<Blog> records = page.getRecords();
        return Result.ok(records);
    }
    @GetMapping("/of/follow")
    public Result queryBlogOfFollow(@RequestParam("lastId") Long max,@RequestParam(value = "offset",defaultValue = "0") Integer offset){
     return blogService.queryBlogOfFollow(max,offset);
    }
    /**
     * 记录一次笔记浏览（HyperLogLog 统计 UV，同一用户重复访问只算 1 次）
     */
    @PostMapping("/uv/{id}")
    public Result addBlogUv(@PathVariable("id") Long id) {
        // 1.获取登录用户
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("请先登录");
        }
        // 2.把当前用户 id 写入笔记的 HyperLogLog（对应 Redis 命令 PFADD）
        String key = RedisConstants.BLOG_UV_KEY + id;
        stringRedisTemplate.opsForHyperLogLog().add(key, user.getId().toString());
        return Result.ok();
    }

    /**
     * 查询笔记浏览 UV
     */
    @GetMapping("/uv/{id}")
    public Result queryBlogUv(@PathVariable("id") Long id) {
        String key = RedisConstants.BLOG_UV_KEY + id;
        // 对应 Redis 命令 PFCOUNT
        Long uv = stringRedisTemplate.opsForHyperLogLog().size(key);
        return Result.ok(uv);
    }

}
