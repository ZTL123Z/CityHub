package com.hmdp.utils;

import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import org.springframework.lang.Nullable;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

public class LoginInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1.获得session
        HttpSession session = request.getSession();

        // 2.获得user
        Object user = (User) session.getAttribute("user");

        // 3.如果user为空，返回false
        if (user == null) {
            // 4.如果user为空，返回401
//            response.setStatus(401);
            return false;
        }

        // 5.存在，保存到ThreadLocal
        UserHolder.saveUser((User)user);
        // 6.返回true
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, @Nullable Exception ex) throws Exception {
       // 移除
        UserHolder.removeUser();
    }


}
