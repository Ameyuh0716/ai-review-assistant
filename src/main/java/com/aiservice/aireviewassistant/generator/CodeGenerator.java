package com.aiservice.aireviewassistant.generator;

import com.baomidou.mybatisplus.generator.FastAutoGenerator;
import com.baomidou.mybatisplus.generator.engine.FreemarkerTemplateEngine;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CodeGenerator {

    public static void main(String[] args) {
        String projectPath = System.getProperty("user.dir");

        FastAutoGenerator.create(
                "jdbc:postgresql://localhost:5432/review_db?currentSchema=public",
                "postgres",
                "postgres"
        )
        // 全局配置
        .globalConfig(builder -> {
            builder.author("aiservice")
                   .outputDir(projectPath + "/src/main/java")
                   .commentDate("2026-06-29")
                   .disableOpenDir();
        })
        // 包配置
        .packageConfig(builder -> {
            builder.parent("com.aiservice.aireviewassistant")
                   .entity("entity")
                   .mapper("mapper")
                   .service("service")
                   .serviceImpl("service.impl")
                   .controller("controller")
                   .pathInfo(null);
        })
        // 策略配置
        .strategyConfig(builder -> {
            builder.addInclude("courses", "review_records", "conversation", "message")
                   .addTablePrefix("")

            // Entity策略
            .entityBuilder()
                   .enableLombok()
                   .enableTableFieldAnnotation()
                   .disableSerialVersionUID()

            // Mapper策略
            .mapperBuilder()
                   .enableBaseResultMap()
                   .enableBaseColumnList()

            // Service策略
            .serviceBuilder()
                   .formatServiceFileName("%sService")
                   .formatServiceImplFileName("%sServiceImpl")

            // Controller策略（不需要可以注释掉）
            .controllerBuilder()
                   .enableRestStyle()
                   .enableHyphenStyle();
        })
        .templateEngine(new FreemarkerTemplateEngine())
        .execute();

        log.info("=== 代码生成完成 ===");
    }
}
