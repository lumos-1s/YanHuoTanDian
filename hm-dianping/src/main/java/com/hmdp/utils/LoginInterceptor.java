package com.hmdp.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.HandlerInterceptor;


import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Slf4j
public class LoginInterceptor implements HandlerInterceptor  {

//    private StringRedisTemplate stringRedisTemplate;
//public LoginInterceptor(StringRedisTemplate stringRedisTemplate){
//    this.stringRedisTemplate = stringRedisTemplate;
//}

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

//        //获取请求头中的token
//        String token = request.getHeader("authorization");
//        if(StrUtil.isBlank(token)){
//            response.setStatus(401);
//            return false;
//        }
//        //基于token获取redis中的用户
//        String key = RedisConstants.LOGIN_USER_KEY + token;
//        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(RedisConstants.LOGIN_USER_KEY + token);
//        //判断用户是否存在
//
//        if(userMap.isEmpty()) {
//            response.setStatus(401);
//            return false;
//        }
//
//        //将查询到的Hash数据转为UserDao对象
//        UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);
//        //存在，保存用户信息到ThreadLocal
//        UserHolder.saveUser(userDTO);
//
//        //刷新token有效期
//        stringRedisTemplate.expire(key, RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);
//

        //放行
        //判断是否需要去拦截(ThreadLocal中是否有用户)
        if(UserHolder.getUser() == null){
            //拦截，未登录
            response.setStatus(401);
            return false;
        }

        return true;
    }

//    @Override
//    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
//        //移除用户
//        UserHolder.removeUser();
//    }
}
