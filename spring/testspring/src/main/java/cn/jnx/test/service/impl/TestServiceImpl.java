package cn.jnx.test.service.impl;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import cn.jnx.spring.annotation.MyService;
import cn.jnx.test.service.TestService;

@MyService
public class TestServiceImpl implements TestService {

    public void hello(HttpServletRequest request, HttpServletResponse response) {
        String name = request.getParameter("name");
        try {
            response.getWriter().write("Hello :" + name);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
