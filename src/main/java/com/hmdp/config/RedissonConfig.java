package com.hmdp.config;


import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration
public class RedissonConfig {

    @Bean
    public RedissonClient redissonClient() {
        //配置类
        Config config = new Config();
        // 配置单节点模式
        config.useSingleServer().setAddress("redis://localhost:6379");
        // 配置密码
        config.useSingleServer().setPassword("123456");
        return Redisson.create(config);
    }
}
