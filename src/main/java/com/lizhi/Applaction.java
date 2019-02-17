package com.lizhi;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication
@MapperScan(basePackages = "com.lizhi.dao")
@EnableTransactionManagement //开启对事务的扫描
public class Applaction {
	public static void main(String[] args) throws Exception {
		SpringApplication.run(Applaction.class, args);
	}
}

