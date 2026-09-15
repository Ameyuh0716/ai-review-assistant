package com.aiservice.aireviewassistant.generator;

import com.baomidou.mybatisplus.generator.FastAutoGenerator;
import com.baomidou.mybatisplus.generator.engine.FreemarkerTemplateEngine;
import lombok.extern.slf4j.Slf4j;

/**
 * MyBatis-Plus 代码生成器。
 * <p>
 * 基于数据库表结构自动生成 Entity、Mapper、Service、Controller 等模板代码，
 * 减少手写样板代码的工作量。使用 Freemarker 作为模板引擎。
 * </p>
 *
 * @author aiservice
 */
@Slf4j
public class CodeGenerator {

    /**
     * 代码生成器入口方法。
     *
     * @param args 命令行参数（当前未使用，可直接运行）
     */
    public static void main(String[] args) {
        // 获取当前项目的根目录路径，作为代码输出根目录
        String projectPath = System.getProperty("user.dir");

        FastAutoGenerator.create(
                "jdbc:postgresql://localhost:5432/review_db?currentSchema=public",
                "postgres",
                "postgres"
        )
        // 全局配置：设置作者、输出目录、注释日期等
        .globalConfig(builder -> {
            builder.author("aiservice")
                   .outputDir(projectPath + "/src/main/java")
                   .commentDate("2026-06-29")
                   .disableOpenDir();
        })
        // 包配置：设置生成的包名及子包名
        .packageConfig(builder -> {
            builder.parent("com.aiservice.aireviewassistant")
                   .entity("entity")
                   .mapper("mapper")
                   .service("service")
                   .serviceImpl("service.impl")
                   .controller("controller")
                   .pathInfo(null);
        })
        // 策略配置：指定要生成的表及各类文件命名/风格策略
        .strategyConfig(builder -> {
            builder.addInclude("courses", "review_records", "conversation", "message")
                   .addTablePrefix("")

            // Entity 策略：启用 Lombok、@TableField 注解，不生成 serialVersionUID
            .entityBuilder()
                   .enableLombok()
                   .enableTableFieldAnnotation()
                   .disableSerialVersionUID()

            // Mapper 策略：生成基础 ResultMap 和 ColumnList
            .mapperBuilder()
                   .enableBaseResultMap()
                   .enableBaseColumnList()

            // Service 策略：定义 Service 接口与实现类的命名格式
            .serviceBuilder()
                   .formatServiceFileName("%sService")
                   .formatServiceImplFileName("%sServiceImpl")

            // Controller 策略：启用 REST 风格与连字符 URL 风格（如不需要可注释掉）
            .controllerBuilder()
                   .enableRestStyle()
                   .enableHyphenStyle();
        })
        // 指定 Freemarker 模板引擎
        .templateEngine(new FreemarkerTemplateEngine())
        // 执行代码生成
        .execute();

        log.info("=== 代码生成完成 ===");
    }
}
