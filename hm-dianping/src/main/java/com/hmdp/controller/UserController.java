package com.hmdp.controller;


import cn.hutool.core.bean.BeanUtil;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.entity.UserInfo;
import com.hmdp.service.IBlogService;
import com.hmdp.service.IUserInfoService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@RestController
@RequestMapping("/user")
public class UserController {

    @Resource
    private IUserService userService;

    @Resource
    private IUserInfoService userInfoService;
    @Autowired
    private IBlogService iBlogService;

    /**
     * 发送手机验证码
     */
    @PostMapping("code")
    public Result sendCode(@RequestParam("phone") String phone, HttpSession session) {
        // TODO 发送短信验证码并保存验证码
        return userService.sendCode(phone, session);
    }

    /**
     * 登录功能
     * @param loginForm 登录参数，包含手机号、验证码；或者手机号、密码
     */
    @PostMapping("/login")
    public Result login(@RequestBody LoginFormDTO loginForm, HttpSession session){
        // TODO 实现登录功能
        return userService.login(loginForm, session);
    }

    /**
     * 登出功能
     * @return 无
     */
    @PostMapping("/logout")
    public Result logout(){
        // TODO 实现登出功能
        return Result.fail("功能未完成");
    }





//    @GetMapping("/me")
//    public Result me(){
//        // TODO 获取当前登录的用户并返回
//        UserDTO user = UserHolder.getUser();
//        log.info("me方法执行了，user={}",user);
//        return Result.ok(user);
//    }
    @GetMapping("/me")
    public Result me(){
        //从ThreadLocal拿到源对象
//        UserDTO source = UserHolder.getUser();
//        //新建对象，复制属性，完全脱离ThreadLocal，不受afterCompletion清理影响
//        UserDTO user = new UserDTO();
//        user.setId(source.getId());
//        user.setNickName(source.getNickName());
//        user.setIcon(source.getIcon());
//        log.info("me方法执行了，user={}",user);
        UserDTO user= UserHolder.getUser();
        return Result.ok(user);
    }



    @GetMapping("/info/{id}")
    public Result info(@PathVariable("id") Long userId){
        // 查询详情
        UserInfo info = userInfoService.getById(userId);
        if (info == null) {
            // 没有详情，应该是第一次查看详情
            return Result.ok();
        }
        info.setCreateTime(null);
        info.setUpdateTime(null);
        // 返回
        return Result.ok(info);
    }
    @GetMapping("/{id}")
    public Result queryUserById(@PathVariable("id") Long userId){
        //查询详情
        User user= userService.getById(userId);
        if (user==null){
            return Result.ok();

        }
        UserDTO userDTO= BeanUtil.copyProperties(user,UserDTO.class);
        return Result.ok(userDTO);

    }
    @PostMapping("/sign")
    public Result sign(){
        // TODO 实现签到功能
        return userService.sign();
    }
    @GetMapping("/sign/count")
    public Result signCount(){
        // TODO 获取当前登录用户已签到的天数
        return userService.signCount();
    }


}
