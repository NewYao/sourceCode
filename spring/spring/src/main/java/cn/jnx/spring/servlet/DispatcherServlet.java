package cn.jnx.spring.servlet;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import cn.jnx.spring.annotation.MyAutoWired;
import cn.jnx.spring.annotation.MyController;
import cn.jnx.spring.annotation.MyRequestMapping;
import cn.jnx.spring.annotation.MyService;

public class DispatcherServlet extends HttpServlet {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    // 配置文件
    private Properties contextConfig = new Properties();
    // 储存扫描的class文件路径，包名加类名（cn.jnx.annotation.MyAutoWired）
    private List<String> classNames = new ArrayList<>();
    // ioc容器
    private Map<String, Object> ioc = new HashMap<>();
    // url映射容器
    private Map<String, Method> handlerMapping = new HashMap<>();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        this.doPost(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            //执行请求
            doDispatch(req, resp);
        } catch (Exception e) {
            e.printStackTrace();
            resp.getWriter().write("500 Exception Detail:" + Arrays.toString(e.getStackTrace()));
        }
    }

    @Override
    public void init(ServletConfig config) throws ServletException {
        // 1.加载配置文件
        doLoadConfig(config.getInitParameter("contextConfigLocation"));
        // 2.扫描配置文件
        doScanner(contextConfig.getProperty("scanPackage"));
        try {
            // 3.实例化相关类
            doInstance();
        } catch (Exception e) {
            e.printStackTrace();
        }
        // 4.完成依赖注入
        doAutoWired();
        // 5.初始化 HandleMapping
        doInitHandlerMapping();
        System.out.println("MY-SPRING:启动完成!");

    }

    private void doDispatch(HttpServletRequest req, HttpServletResponse resp)
            throws IOException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
        String url = req.getRequestURI();
        String contextPath = req.getContextPath();
        url = url.replaceAll(contextPath, "").replaceAll("/+", "/");
        if (!handlerMapping.containsKey(url)) {
            throw new RuntimeException(url + "   404 Not Found!!");
//            resp.getWriter().write("404 Not Found!!");
        }
        Method method = this.handlerMapping.get(url);
        String beanName = toLowerFirstCase(method.getDeclaringClass().getSimpleName());
        method.invoke(ioc.get(beanName), new Object[] { req, resp });
    }

    private void doInitHandlerMapping() {
        if (ioc.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Object> entry : ioc.entrySet()) {
            Class<? extends Object> clazz = entry.getValue().getClass();
            if (!clazz.isAnnotationPresent(MyController.class)) {
                return;
            }
            String baseUrl = "";
            if (clazz.isAnnotationPresent(MyRequestMapping.class)) {
                MyRequestMapping requestMapping = clazz.getAnnotation(MyRequestMapping.class);
                baseUrl = requestMapping.value();
            }

            for (Method method : clazz.getMethods()) {
                if (!method.isAnnotationPresent(MyRequestMapping.class)) {
                    continue;
                }
                MyRequestMapping requestMapping = method.getAnnotation(MyRequestMapping.class);
                String url = ("/" + baseUrl + "/" + requestMapping.value()).replaceAll("/+", "/");
                handlerMapping.put(url, method);
                System.out.println("Mapped" + url + "," + method);
            }
        }
    }

    private void doAutoWired() {
        if (ioc.isEmpty()) {
            return;
        } 
        for (Map.Entry<String, Object> entry : ioc.entrySet()) {
            Field[] fields = entry.getValue().getClass().getDeclaredFields();
            for (Field field : fields) {
                if (!field.isAnnotationPresent(MyAutoWired.class)) {
                    return;
                }
                MyAutoWired myAutoWired = field.getAnnotation(MyAutoWired.class);
                // 获取自定义注解的值
                String beanName = myAutoWired.value().trim();
                if ("".equals(beanName)) {// 若自定义注解未设置值，则取类型名
                    beanName = field.getType().getName();
                }
                // 允许强制访问
                field.setAccessible(true);
                try {
                    field.set(entry.getValue(), ioc.get(beanName));
                } catch (IllegalArgumentException | IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
        }

    }

    private void doInstance() throws Exception {
        if (classNames.isEmpty()) {
            return;
        }
        try {
            for (String className : classNames) {
                // forName()==>将.class文件加载到jvm内,并且对类进行解释,执行类中的static静态代码快
                Class<?> clazz = Class.forName(className);
                // 是否包含MyController注解
                if (clazz.isAnnotationPresent(MyController.class)) {
                    String beanName = toLowerFirstCase(clazz.getSimpleName());// 默认首字母小写类名
                    // 使用类名首字母小写作为key,创建该类的新的对象作为value，保存到ioc中
                    ioc.put(beanName, clazz.newInstance());
                } else if (clazz.isAnnotationPresent(MyService.class)) {// 是否包含MyService注解
                    // 1.默认类名小写
                    String beanName = toLowerFirstCase(clazz.getSimpleName());// 默认首字母小写类名
                    // 2.自定义命名
                    MyService service = clazz.getAnnotation(MyService.class);
                    if (!"".equals(service.value())) {// 添加自定义命名
                        beanName = service.value();// 使用用户命名的name
                    }
                    Object instance = clazz.newInstance();
                    ioc.put(beanName, instance);
                    // 3.把service的接口实例化为实现类,实例化实现类
                    for (Class<?> i : clazz.getInterfaces()) {// 如果有实现类
                        if (ioc.containsKey(i.getName())) {
                            throw new Exception("The beanName is exists!");
                        }
                        ioc.put(i.getName(), instance);
                    }
                } else {
                    continue;
                }
            }
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (InstantiationException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }

    }

    // 首字母小写方法
    private String toLowerFirstCase(String simpleName) {
        char[] chars = simpleName.toCharArray();
        chars[0] += 32;
        return String.valueOf(chars);
    }

    private void doScanner(String scanPackage) {
        // 获取本地存放class文件目录
        URL url = this.getClass().getClassLoader().getResource("/" + scanPackage.replace(".", "/"));
        // file:/D:/apache-tomcat-windows-x64/apache-tomcat-8.5.37/webapps/my-spring/WEB-INF/classes/cn/jnx/
        // 获取声明需要扫描的class文件所声明的文件夹
        File classPath = new File(url.getFile().replace("%20", " "));//处理文件名空格
        // listFiles()==>返回某个目录下所有文件和目录的绝对路径，返回的是File数组
        for (File file : classPath.listFiles()) {
            if (file.isDirectory()) {// 如果是文件夹，递归
                doScanner(scanPackage + "." + file.getName());
            } else if (file.getName().endsWith(".class")) {
                String className = scanPackage + "." + file.getName().replace(".class", "");
                classNames.add(className);
            }
        }
    }

    private void doLoadConfig(String contextConfigLocation) {

        try (InputStream is = this.getClass().getClassLoader().getResourceAsStream(contextConfigLocation);) {
            contextConfig.load(is);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

}
