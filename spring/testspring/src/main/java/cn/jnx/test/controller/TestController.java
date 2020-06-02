package cn.jnx.test.controller;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import cn.jnx.spring.annotation.MyAutoWired;
import cn.jnx.spring.annotation.MyController;
import cn.jnx.spring.annotation.MyRequestMapping;
import cn.jnx.test.service.TestService;

@MyController
public class TestController {
    
    @MyAutoWired
    private TestService testService;
    @MyRequestMapping("/hello")
    public void hello(HttpServletRequest request,HttpServletResponse response) {
        testService.hello(request, response);
    }
}
