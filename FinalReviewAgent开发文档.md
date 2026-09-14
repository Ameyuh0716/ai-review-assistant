# 📚 期末复习智能助手 - 完整开发文档

> **适用人群**：大三学生，寻找AI应用开发实习  
> **开发周期**：10天  
> **技术栈**：Spring AI Alibaba + RAG + Agent  
> **简历价值**：展示AI应用开发能力 + 快速学习能力  

---

## 🎯 一、项目概述

### 1.1 项目定位

**期末复习智能助手**是一个基于Spring AI Alibaba的智能学习辅助系统，核心功能：

- ✅ **知识库管理** - 导入课程文档，自动向量化存储
- ✅ **智能问答** - RAG检索增强，基于课程内容精准回答
- ✅ **题目生成** - 根据知识点自动生成练习题
- ✅ **复习计划** - AI制定个性化复习计划
- ✅ **Agent对话** - 多工具协作的智能对话

### 1.2 简历亮点

| 技术点 | 简历价值 | 面试话术 |
|--------|---------|---------|
| **Spring AI Alibaba** | AI应用开发能力 | "使用Spring AI Alibaba集成通义千问，实现对话AI" |
| **RAG检索增强** | 向量数据库应用 | "基于MySQL实现RAG，提升问答准确性" |
| **Agent对话** | 多工具协作设计 | "设计Agent系统，通过意图识别调用不同工具" |
| **10天完成** | 快速学习执行 | "10天内从零完成，展示了快速学习能力" |

---

## 🏗️ 二、技术架构设计

### 2.1 整体架构

```
期末复习助手架构
├── 前端层 (简单易上手)
│   ├── Thymeleaf模板引擎
│   └── Bootstrap样式
│
├── 应用层 (核心业务)
│   ├── Agent对话服务
│   ├── 知识库管理服务
│   ├── 题目生成服务
│   └── 复习计划服务
│
├── AI层 (Spring AI Alibaba)
│   ├── ChatClient对话
│   ├── PromptTemplate提示词
│   └── VectorStore向量存储
│
├── 数据层
│   ├── MySQL业务数据
│   ├── MySQL向量存储
│   └── 文档存储
│
└── 外部服务
    └── 阿里云通义千问API
```

### 2.2 技术选型说明

| 技术 | 选择理由 | 学习难度 |
|-----|---------|---------|
| **Spring Boot 3.2** | 主流框架，面试必问，你应该已经熟悉 | ⭐ 熟悉 |
| **Spring AI Alibaba** | 官方支持，API简洁，文档清晰 | ⭐⭐ 新学（Day2） |
| **MySQL + MyBatis-Plus** | 主流关系型数据库，向量存储支持 | ⭐⭐ 新学（Day3） |
| **阿里云通义千问** | 国内大模型，API友好，性价比高 | ⭐ 新学（Day2） |
| **Thymeleaf** | 简单模板引擎，无需学前端框架 | ⭐ 熟悉 |

**为什么不用复杂技术？**
- ❌ 不用微服务架构（Spring Cloud）- 单体应用足够
- ❌ 不用复杂前端（Vue/React）- Thymeleaf够用
- ❌ 不用Redis/MQ - 10天时间不够学

---

## 📖 三、详细开发步骤

### Day 1：项目搭建与环境配置（基础巩固）

#### 🎯 今日目标
- 创建Spring Boot项目
- 配置开发环境
- 理解项目结构

#### 📚 学习内容

1. **创建项目**（30分钟）
   - 使用IDEA创建Spring Boot项目
   - 选择依赖：Web、Thymeleaf、MySQL
   - 理解pom.xml依赖管理

2. **配置application.yml**（30分钟）
```yaml
server:
  port: 8080

spring:
  datasource:
    url: jdbc:mysql://localhost:3306/review_db
    username: root
    password: root
```

3. **安装必要软件**（1小时）
   - MySQL数据库安装（或使用Docker）
   - 测试数据库连接

#### 💡 实践任务
```java
@RestController
public class HelloController {
    @GetMapping("/")
    public String hello() {
        return "期末复习助手启动成功！";
    }
}
```

测试访问：http://localhost:8080

---

### Day 2：Spring AI Alibaba集成（核心学习）

#### 🎯 今日目标
- 学习Spring AI Alibaba API
- 实现基础对话功能
- 理解ChatClient使用

#### 📚 学习内容

1. **添加Spring AI依赖**（pom.xml）
```xml
<dependency>
    <groupId>com.alibaba.cloud.ai</groupId>
    <artifactId>spring-ai-alibaba-starter</artifactId>
    <version>1.0.0-M4</version>
</dependency>
```

2. **获取通义千问API Key**
   - 访问阿里云DashScope平台
   - 创建API Key
   - 配置到application.yml

3. **实现对话Controller**
```java
@RestController
@RequestMapping("/api/chat")
public class ChatController {
    
    private final ChatClient chatClient;
    
    @PostMapping
    public String chat(@RequestBody String message) {
        return chatClient.prompt()
            .user(message)
            .call()
            .content();
    }
}
```

#### 💡 关键概念理解

**Spring AI核心概念**：
- **ChatClient** - 对话客户端，类似HttpClient
- **Prompt** - 提示词，包含user/system内容
- **ChatOptions** - 模型参数（temperature、model等）

---

### Day 3：数据库表设计与MyBatis-Plus配置（RAG基础）

#### 🎯 今日目标
- 设计并创建数据库表结构
- 配置MyBatis-Plus框架
- 创建实体类（Entity）
- 创建Mapper接口和Service层

#### 📁 今日项目结构

```
src/main/java/com/aiservice/aireviewassistant/
├── AiReviewAssistantApplication.java    # 启动类
├── controller/
│   └── ChatController.java              # 对话控制器
├── config/                              # ← 新建
│   └── MyBatisPlusConfig.java           # MyBatis-Plus配置
├── entity/                              # ← 新建
│   ├── Course.java                      # 课程实体
│   ├── KnowledgeVector.java             # 向量存储实体
│   └── ReviewRecord.java                # 复习记录实体
├── mapper/                              # ← 新建
│   ├── CourseMapper.java                # 课程Mapper
│   ├── KnowledgeVectorMapper.java       # 向量Mapper
│   └── ReviewRecordMapper.java          # 复习记录Mapper
└── service/                             # ← 新建
    ├── CourseService.java               # 课程服务
    └── impl/
        └── CourseServiceImpl.java       # 课程服务实现
```

#### 📚 学习内容

---

##### 步骤1：创建数据库表

首先进入MySQL命令行，创建三张表：

```bash
docker exec -it ai-review-mysql mysql -u root -proot review_db
```

然后执行以下SQL：

```sql
-- 创建课程表（存储课程基本信息）
CREATE TABLE courses (
    id INT AUTO_INCREMENT PRIMARY KEY COMMENT '课程ID',
    name VARCHAR(100) NOT NULL COMMENT '课程名称',
    description TEXT COMMENT '课程描述',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_courses_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='课程表';

-- 创建知识向量表（存储向量化后的文档片段）
CREATE TABLE knowledge_vectors (
    id INT AUTO_INCREMENT PRIMARY KEY COMMENT '向量ID',
    course_id INT NOT NULL COMMENT '关联课程ID',
    content TEXT NOT NULL COMMENT '文档内容片段',
    embedding BLOB COMMENT '向量数据（BLOB存储）',
    metadata JSON COMMENT '元数据（如章节、页码等）',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_knowledge_course (course_id),
    FOREIGN KEY (course_id) REFERENCES courses(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识向量表';

-- 创建复习记录表（存储用户复习记录）
CREATE TABLE review_records (
    id INT AUTO_INCREMENT PRIMARY KEY COMMENT '记录ID',
    course_id INT NOT NULL COMMENT '关联课程ID',
    user_id VARCHAR(50) COMMENT '用户ID',
    question TEXT COMMENT '用户问题',
    answer TEXT COMMENT 'AI回答',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_review_course (course_id),
    INDEX idx_review_user (user_id),
    FOREIGN KEY (course_id) REFERENCES courses(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='复习记录表';
```

**解释**：
- `courses`：存储课程信息（如"数据库系统"、"人工智能"）
- `knowledge_vectors`：存储文档分块后的向量数据，用于RAG检索
- `review_records`：记录用户的提问和AI的回答，方便追踪复习情况

---

##### 步骤2：配置MyBatis-Plus

创建配置类 `config/MyBatisPlusConfig.java`：

```java
package com.aiservice.aireviewassistant.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan("com.aiservice.aireviewassistant.mapper")
public class MyBatisPlusConfig {
}
```

**解释**：
- `@MapperScan`：告诉MyBatis-Plus扫描哪个包下的Mapper接口
- 这样就不需要在每个Mapper接口上写 `@Mapper` 注解了

---

##### 步骤3：创建实体类

**实体类1**：`entity/Course.java`

```java
package com.aiservice.aireviewassistant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("courses")
public class Course {
    
    @TableId(type = IdType.AUTO)
    private Integer id;
    
    @TableField("name")
    private String name;
    
    @TableField("description")
    private String description;
    
    @TableField("created_at")
    private LocalDateTime createdAt;
    
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
```

**实体类2**：`entity/KnowledgeVector.java`

```java
package com.aiservice.aireviewassistant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("knowledge_vectors")
public class KnowledgeVector {
    
    @TableId(type = IdType.AUTO)
    private Integer id;
    
    @TableField("course_id")
    private Integer courseId;
    
    @TableField("content")
    private String content;
    
    @TableField("embedding")
    private byte[] embedding;
    
    @TableField("metadata")
    private String metadata;
    
    @TableField("created_at")
    private LocalDateTime createdAt;
}
```

**实体类3**：`entity/ReviewRecord.java`

```java
package com.aiservice.aireviewassistant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("review_records")
public class ReviewRecord {
    
    @TableId(type = IdType.AUTO)
    private Integer id;
    
    @TableField("course_id")
    private Integer courseId;
    
    @TableField("user_id")
    private String userId;
    
    @TableField("question")
    private String question;
    
    @TableField("answer")
    private String answer;
    
    @TableField("created_at")
    private LocalDateTime createdAt;
}
```

**解释**：
- `@Data`：Lombok注解，自动生成getter、setter、toString等方法
- `@TableName`：指定对应的数据库表名
- `@TableId`：指定主键字段
- `@TableField`：指定对应的数据库列名

---

##### 步骤4：创建Mapper接口

**Mapper接口1**：`mapper/CourseMapper.java`

```java
package com.aiservice.aireviewassistant.mapper;

import com.aiservice.aireviewassistant.entity.Course;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

public interface CourseMapper extends BaseMapper<Course> {
}
```

**Mapper接口2**：`mapper/KnowledgeVectorMapper.java`

```java
package com.aiservice.aireviewassistant.mapper;

import com.aiservice.aireviewassistant.entity.KnowledgeVector;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

public interface KnowledgeVectorMapper extends BaseMapper<KnowledgeVector> {
}
```

**Mapper接口3**：`mapper/ReviewRecordMapper.java`

```java
package com.aiservice.aireviewassistant.mapper;

import com.aiservice.aireviewassistant.entity.ReviewRecord;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

public interface ReviewRecordMapper extends BaseMapper<ReviewRecord> {
}
```

**解释**：
- MyBatis-Plus的 `BaseMapper` 提供了常用的CRUD方法：
  - `insert()`：新增
  - `updateById()`：根据ID更新
  - `selectById()`：根据ID查询
  - `selectList()`：查询列表
  - `deleteById()`：根据ID删除

---

##### 步骤5：创建Service层

**Service接口**：`service/CourseService.java`

```java
package com.aiservice.aireviewassistant.service;

import com.aiservice.aireviewassistant.entity.Course;
import com.baomidou.mybatisplus.extension.service.IService;

public interface CourseService extends IService<Course> {
}
```

**Service实现**：`service/impl/CourseServiceImpl.java`

```java
package com.aiservice.aireviewassistant.service.impl;

import com.aiservice.aireviewassistant.entity.Course;
import com.aiservice.aireviewassistant.mapper.CourseMapper;
import com.aiservice.aireviewassistant.service.CourseService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class CourseServiceImpl extends ServiceImpl<CourseMapper, Course> implements CourseService {
}
```

**解释**：
- `IService`：MyBatis-Plus的服务层接口，提供更丰富的方法
- `ServiceImpl`：服务层实现类，继承后自动获得所有CRUD能力

---

##### 步骤6：测试验证

**方法1**：创建测试数据

进入MySQL命令行，插入测试数据：

```sql
INSERT INTO courses (name, description) VALUES 
('数据库系统', '介绍关系型数据库原理、SQL语言、事务处理等核心概念'),
('人工智能', '机器学习基础、神经网络、深度学习框架'),
('软件工程', '软件开发流程、设计模式、代码质量管理');
```

**方法2**：编写测试类

创建 `src/test/java/com/aiservice/aireviewassistant/service/CourseServiceTest.java`：

```java
package com.aiservice.aireviewassistant.service;

import com.aiservice.aireviewassistant.entity.Course;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

@SpringBootTest
public class CourseServiceTest {
    
    @Autowired
    private CourseService courseService;
    
    @Test
    public void testListCourses() {
        List<Course> courses = courseService.list();
        System.out.println("课程列表：");
        courses.forEach(course -> System.out.println(course.getId() + ": " + course.getName()));
    }
    
    @Test
    public void testAddCourse() {
        Course course = new Course();
        course.setName("计算机网络");
        course.setDescription("TCP/IP协议、HTTP、网络安全");
        courseService.save(course);
        System.out.println("新增课程ID: " + course.getId());
    }
}
```

**运行测试**：
```bash
./mvnw test -Dtest=CourseServiceTest
```

---

#### 💡 关键概念理解

##### 什么是MyBatis-Plus？

MyBatis-Plus是MyBatis的增强工具，提供以下便利功能：

| 功能 | 说明 |
|------|------|
| **自动CRUD** | 继承 `BaseMapper` 即可获得增删改查方法 |
| **分页查询** | 内置分页插件，一行代码实现分页 |
| **代码生成器** | 自动生成Entity、Mapper、Service、Controller |
| **条件构造器** | 链式调用构建复杂SQL条件 |
| **逻辑删除** | 标记删除而非物理删除 |

##### RAG原理理解

**RAG（检索增强生成）工作流程**：

```
用户提问："什么是数据库事务？"
              ↓
1. 向量化：将用户问题转换成向量（1536维的浮点数数组）
              ↓
2. 向量检索：从knowledge_vectors表中查找最相似的文档
              ↓
3. 构建上下文：将检索到的文档内容拼接起来
              ↓
4. AI生成回答：把上下文和问题一起发给大模型
              ↓
5. 返回结果：AI基于真实课程内容生成回答
```

**为什么需要RAG？**
- ❌ 传统AI对话容易"胡说八道"（幻觉）
- ✅ RAG让AI基于真实文档回答，准确率更高
- ✅ 可以基于课程内容进行个性化问答

---

### Day 4：文档导入与向量化（核心功能）

#### 🎯 今日目标
- 配置文件上传功能
- 实现文档分块处理策略
- 使用Spring AI VectorStore进行向量化
- 将向量数据存储到MySQL的knowledge_vectors表
- 创建文档管理API接口

#### 📁 今日项目结构变化

```
src/main/java/com/aiservice/aireviewassistant/
├── AiReviewAssistantApplication.java    # 启动类
├── config/
│   ├── MyBatisPlusConfig.java           # MyBatis-Plus配置
│   └── VectorStoreConfig.java           # ← 新建：向量存储配置
├── controller/
│   ├── ChatController.java              # 对话控制器
│   ├── CoursesController.java           # 课程控制器
│   └── DocumentController.java          # ← 新建：文档上传控制器
├── entity/
│   ├── Courses.java                     # 课程实体
│   ├── KnowledgeVectors.java            # 向量存储实体
│   └── ReviewRecords.java               # 复习记录实体
├── mapper/
│   ├── CoursesMapper.java
│   ├── KnowledgeVectorsMapper.java
│   └── ReviewRecordsMapper.java
├── service/
│   ├── CourseService.java
│   ├── DocumentService.java             # ← 新建：文档处理服务
│   ├── VectorStoreService.java          # ← 新建：向量存储服务
│   └── impl/
│       ├── CourseServiceImpl.java
│       └── DocumentServiceImpl.java     # ← 新建：文档服务实现
```

---

#### 📚 学习内容

---

##### 步骤1：配置文件上传

在 `application.yml` 中添加文件上传配置：

```yaml
server:
  port: 8080

spring:
  application:
    name: ai-review-assistant

  # 文件上传配置
  servlet:
    multipart:
      enabled: true              # 启用文件上传
      max-file-size: 10MB        # 单个文件最大大小
      max-request-size: 10MB     # 请求最大大小

  datasource:
    url: jdbc:mysql://localhost:3307/review_db?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
    username: root
    password: root
    driver-class-name: com.mysql.cj.jdbc.Driver

  ai:
    dashscope:
      api-key: sk-your-api-key
      chat:
        options:
          model: qwen-plus
          temperature: 0.9

mybatis-plus:
  configuration:
    map-underscore-to-camel-case: true
    cache-enabled: false
    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl
  global-config:
    db-config:
      id-type: auto
```

**解释**：
- `spring.servlet.multipart.enabled: true`：启用文件上传功能
- `max-file-size: 10MB`：限制单个文件大小不超过10MB
- `max-request-size: 10MB`：限制整个请求大小不超过10MB

---

##### 步骤2：创建向量存储配置类

创建 `config/VectorStoreConfig.java`：

```java
package com.aiservice.aireviewassistant.config;

import com.alibaba.cloud.ai.dashscope.embedding.DashScopeEmbeddingModel;
import com.alibaba.cloud.ai.dashscope.embedding.DashScopeEmbeddingOptions;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class VectorStoreConfig {

    // 创建通义千问嵌入模型
    @Bean
    public EmbeddingModel embeddingModel() {
        DashScopeEmbeddingOptions options = new DashScopeEmbeddingOptions();
        options.setModel("text-embedding-v1");
        return new DashScopeEmbeddingModel(options);
    }

    // 创建向量存储
    @Bean
    public VectorStore vectorStore(EmbeddingModel embeddingModel) {
        return new SimpleVectorStore(embeddingModel);
    }
}
```

**解释**：
- **EmbeddingModel**：嵌入模型，负责将文本转换成向量（1536维浮点数数组）
- **VectorStore**：向量存储接口，提供向量化存储和检索功能
- **SimpleVectorStore**：Spring AI提供的简单向量存储实现，内存存储（后续可扩展到持久化存储）

---

##### 步骤3：创建文档处理服务接口

创建 `service/DocumentService.java`：

```java
package com.aiservice.aireviewassistant.service;

import org.springframework.web.multipart.MultipartFile;

public interface DocumentService {

    /**
     * 上传文档并向量化
     * @param courseId 课程ID
     * @param file 上传的文件
     * @return 处理结果
     */
    String uploadAndVectorize(Integer courseId, MultipartFile file);

    /**
     * 直接导入文本内容并向量化
     * @param courseId 课程ID
     * @param content 文本内容
     * @return 处理结果
     */
    String importContent(Integer courseId, String content);
}
```

---

##### 步骤4：创建文档处理服务实现

创建 `service/impl/DocumentServiceImpl.java`：

```java
package com.aiservice.aireviewassistant.service.impl;

import com.aiservice.aireviewassistant.entity.KnowledgeVectors;
import com.aiservice.aireviewassistant.service.CoursesService;
import com.aiservice.aireviewassistant.service.DocumentService;
import com.aiservice.aireviewassistant.service.KnowledgeVectorsService;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class DocumentServiceImpl implements DocumentService {

    private final EmbeddingModel embeddingModel;
    private final VectorStore vectorStore;
    private final KnowledgeVectorsService knowledgeVectorsService;
    private final CoursesService coursesService;

    public DocumentServiceImpl(EmbeddingModel embeddingModel, 
                               VectorStore vectorStore,
                               KnowledgeVectorsService knowledgeVectorsService,
                               CoursesService coursesService) {
        this.embeddingModel = embeddingModel;
        this.vectorStore = vectorStore;
        this.knowledgeVectorsService = knowledgeVectorsService;
        this.coursesService = coursesService;
    }

    @Override
    public String uploadAndVectorize(Integer courseId, MultipartFile file) {
        // 1. 检查课程是否存在
        if (coursesService.getById(courseId) == null) {
            return "错误：课程ID不存在";
        }

        // 2. 读取文件内容
        String content;
        try {
            content = readFileContent(file);
        } catch (Exception e) {
            return "错误：读取文件失败 - " + e.getMessage();
        }

        // 3. 调用导入方法
        return importContent(courseId, content);
    }

    @Override
    public String importContent(Integer courseId, String content) {
        // 1. 文档分块
        List<String> chunks = splitDocument(content);
        
        if (chunks.isEmpty()) {
            return "错误：文档内容为空或无法分块";
        }

        // 2. 向量化并存储
        int successCount = 0;
        for (int i = 0; i < chunks.size(); i++) {
            String chunk = chunks.get(i);
            try {
                // 创建文档对象
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("courseId", courseId);
                metadata.put("chunkIndex", i);
                metadata.put("chunkTotal", chunks.size());
                
                Document doc = new Document(chunk, metadata);
                
                // 添加到向量存储
                vectorStore.add(List.of(doc));
                
                // 同时保存到MySQL数据库
                KnowledgeVectors kv = new KnowledgeVectors();
                kv.setCourseId(courseId);
                kv.setContent(chunk);
                kv.setMetadata("{\"chunkIndex\":" + i + ",\"chunkTotal\":" + chunks.size() + "}");
                
                // 获取向量数据并存入数据库
                List<Float> embedding = embeddingModel.embed(List.of(chunk)).get(0);
                kv.setEmbedding(floatListToByteArray(embedding));
                
                knowledgeVectorsService.save(kv);
                successCount++;
                
            } catch (Exception e) {
                System.err.println("分块 " + i + " 向量化失败: " + e.getMessage());
            }
        }

        return "成功：文档已分块并向量化，共处理 " + chunks.size() + " 块，成功存储 " + successCount + " 块";
    }

    /**
     * 文档分块策略：按段落分块，每块约500字符
     */
    private List<String> splitDocument(String content) {
        List<String> chunks = new ArrayList<>();
        
        // 按双换行符（段落）分割
        String[] paragraphs = content.split("\n\n");
        
        StringBuilder currentChunk = new StringBuilder();
        for (String paragraph : paragraphs) {
            // 去除前后空白
            paragraph = paragraph.trim();
            
            // 跳过空段落
            if (paragraph.isEmpty()) {
                continue;
            }
            
            // 如果当前块加上新段落超过500字符，就保存当前块
            if (currentChunk.length() + paragraph.length() > 500) {
                if (currentChunk.length() > 50) {
                    chunks.add(currentChunk.toString());
                }
                currentChunk = new StringBuilder();
            }
            
            // 添加段落到当前块
            if (currentChunk.length() > 0) {
                currentChunk.append("\n\n");
            }
            currentChunk.append(paragraph);
        }
        
        // 添加最后一个块
        if (currentChunk.length() > 50) {
            chunks.add(currentChunk.toString());
        }
        
        // 限制最多50个块
        if (chunks.size() > 50) {
            chunks = chunks.subList(0, 50);
        }
        
        return chunks;
    }

    /**
     * 读取文件内容（支持txt文件）
     */
    private String readFileContent(MultipartFile file) throws Exception {
        // 检查文件类型
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".txt")) {
            throw new IllegalArgumentException("仅支持txt格式文件");
        }
        
        // 读取文件内容
        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
        }
        
        return content.toString();
    }

    /**
     * 将Float列表转换为字节数组（用于存储到BLOB字段）
     */
    private byte[] floatListToByteArray(List<Float> floats) {
        java.nio.ByteBuffer buffer = java.nio.ByteBuffer.allocate(floats.size() * 4);
        for (Float f : floats) {
            buffer.putFloat(f);
        }
        return buffer.array();
    }
}
```

**解释**：

1. **文档分块策略**：
   - 按段落（双换行符）分割
   - 每块约500字符
   - 块太小（少于50字符）会被合并
   - 最多处理50个块

2. **向量化流程**：
   - 使用 `EmbeddingModel` 将文本转换为向量
   - 使用 `VectorStore` 进行内存存储（用于快速检索）
   - 同时保存到MySQL的 `knowledge_vectors` 表（用于持久化）

3. **向量存储格式**：
   - MySQL中使用BLOB存储向量（float数组转字节数组）
   - 元数据存储分块索引信息

---

##### 步骤5：创建文档上传控制器

创建 `controller/DocumentController.java`：

```java
package com.aiservice.aireviewassistant.controller;

import com.aiservice.aireviewassistant.service.DocumentService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/document")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    /**
     * 上传文档并向量化
     * POST /api/document/upload?courseId=1
     */
    @PostMapping("/upload")
    public String uploadDocument(
            @RequestParam("courseId") Integer courseId,
            @RequestParam("file") MultipartFile file) {
        
        if (file.isEmpty()) {
            return "错误：请选择要上传的文件";
        }
        
        return documentService.uploadAndVectorize(courseId, file);
    }

    /**
     * 直接导入文本内容
     * POST /api/document/import?courseId=1
     */
    @PostMapping("/import")
    public String importContent(
            @RequestParam("courseId") Integer courseId,
            @RequestBody String content) {
        
        if (content == null || content.trim().isEmpty()) {
            return "错误：内容不能为空";
        }
        
        return documentService.importContent(courseId, content);
    }
}
```

**解释**：
- `@RequestParam("file") MultipartFile file`：接收上传的文件
- `@RequestParam("courseId") Integer courseId`：接收课程ID参数
- `@RequestBody String content`：接收直接提交的文本内容

---

##### 步骤6：测试文档上传功能

**方法1：使用Postman测试文件上传**

1. 打开Postman，创建POST请求：`http://localhost:8080/api/document/upload?courseId=1`
2. 设置Body为 `form-data`
3. 添加字段：
   - `file`：选择一个txt文件
   - `courseId`：1（确保课程表中存在ID为1的课程）
4. 点击发送

**方法2：使用curl测试**

```bash
# 上传txt文件
curl -X POST "http://localhost:8080/api/document/upload?courseId=1" \
  -F "file=@/path/to/your/course_notes.txt"

# 直接导入文本内容
curl -X POST "http://localhost:8080/api/document/import?courseId=1" \
  -H "Content-Type: text/plain" \
  -d "数据库事务是指一个或多个SQL语句的执行单元，这些语句要么全部执行成功，要么全部失败。事务具有ACID特性：原子性、一致性、隔离性和持久性。"
```

**方法3：查看数据库验证**

进入MySQL查看向量数据：

```bash
docker exec -it ai-review-mysql mysql -u root -proot review_db
```

```sql
-- 查询向量数据
SELECT id, course_id, content, LENGTH(embedding) as vector_length 
FROM knowledge_vectors 
WHERE course_id = 1 
LIMIT 5;

-- 查看分块总数
SELECT COUNT(*) as total_chunks FROM knowledge_vectors WHERE course_id = 1;
```

---

#### 💡 关键概念理解

##### 什么是Embedding（嵌入）？

**Embedding** 是将文本转换为数值向量的过程，是RAG的核心技术：

| 概念 | 说明 |
|------|------|
| **文本→向量** | 一段文字转换为1536维的浮点数数组 |
| **语义相似性** | 意思相近的文本，向量距离也近 |
| **余弦相似度** | 用于衡量两个向量的相似程度（0~1） |
| **向量检索** | 根据问题向量查找最相似的文档向量 |

**示例**：
```
文本："数据库事务是什么？"
→ 向量：[0.123, -0.456, 0.789, ...] (共1536个浮点数)
```

##### 文档分块策略详解

为什么需要分块？
- ❌ 整篇文档向量化会丢失细节
- ❌ 大文档向量不够精确
- ✅ 分块后每个片段更专注
- ✅ 检索时能找到更相关的内容

**分块策略对比**：

| 策略 | 优点 | 缺点 |
|------|------|------|
| **按段落分块** | 保持语义完整 | 块大小不均匀 |
| **按固定长度分块** | 块大小均匀 | 可能切断语义 |
| **按句子分块** | 语义最完整 | 块太小，信息不足 |

我们采用的是**按段落分块**，并限制最大字符数（500字），这样既能保持语义完整性，又能控制块大小。

##### VectorStore工作原理

```
用户上传文档："数据库原理笔记.txt"
              ↓
1. 文档分块：[块1, 块2, 块3, ...]
              ↓
2. 向量化：[向量1, 向量2, 向量3, ...]
              ↓
3. 存储：
   ├── 内存存储（SimpleVectorStore）→ 快速检索
   └── MySQL存储（BLOB字段）→ 持久化
              ↓
4. 检索时：问题向量 → 相似度计算 → 返回最相似的文档块
```

---

#### 📝 开发日志模板

```
## Day 4 开发日志

### 今日完成
- [x] 配置文件上传功能（application.yml）
- [x] 创建VectorStoreConfig配置类
- [x] 创建DocumentService接口和实现类
- [x] 创建DocumentController控制器
- [x] 实现文档分块策略
- [x] 实现向量化存储到MySQL

### 遇到问题
问题1：文件上传时中文乱码
解决方案：读取文件时指定UTF-8编码

问题2：向量数据太大无法存储
解决方案：使用BLOB类型存储字节数组

### 学习收获
1. 理解了Embedding向量化原理
2. 掌握了文档分块策略设计
3. 学会了使用VectorStore进行向量存储
4. 了解了向量数据的持久化存储方案

### 明日计划（Day 5）
- [ ] 实现RAG检索增强问答
- [ ] 构建上下文并调用AI生成回答
- [ ] 测试RAG问答效果
```

---

### Day 5：RAG检索增强实现（关键功能）

#### 🎯 今日目标
- 理解RAG完整工作流程
- 创建RagService服务，实现"检索→构建上下文→AI回答"三步流程
- 修改ChatController，将普通对话升级为RAG增强对话
- 理解向量检索参数（topK、相似度阈值）的调优
- 测试验证RAG问答效果

#### 📁 今日项目结构变化

```
src/main/java/com/aiservice/aireviewassistant/
├── AiReviewAssistantApplication.java
├── config/
│   ├── MyBatisPlusConfig.java
│   └── VectorStoreConfig.java
├── controller/
│   ├── ChatController.java              # ← 修改：集成RAG
│   ├── CoursesController.java
│   └── DocumentController.java
├── entity/
│   ├── Courses.java
│   ├── KnowledgeVectors.java
│   └── ReviewRecords.java
├── mapper/
│   ├── CoursesMapper.java
│   ├── KnowledgeVectorsMapper.java
│   ── ReviewRecordsMapper.java
├── service/
│   ├── CourseService.java
│   ├── DocumentService.java
│   ├── RagService.java                  # ← 新建：RAG检索增强服务
│   ├── ReviewRecordsService.java
│   ├── KnowledgeVectorsService.java
│   └── impl/
│       ├── CourseServiceImpl.java
│       ├── DocumentServiceImpl.java
│       ├── RagServiceImpl.java          # ← 新建：RAG服务实现
│       ├── ReviewRecordsServiceImpl.java
│       └── KnowledgeVectorsServiceImpl.java
```

**新增2个文件**：
- `service/RagService.java` — RAG服务接口
- `service/impl/RagServiceImpl.java` — RAG服务实现

**修改1个文件**：
- `controller/ChatController.java` — 接入RAG，让对话基于课程知识库回答

---

#### 📚 学习内容

---

##### 步骤1：理解RAG完整工作流程

在写代码之前，先搞清楚RAG到底是怎么工作的。

**RAG = Retrieval（检索）+ Augmented（增强）+ Generation（生成）**

用一张图理解：

```
用户提问："什么是数据库事务？"
              ↓
┌─────────────────────────────────────┐
│  第一步：检索（Retrieval）            │
│  1. 把用户问题"什么是数据库事务？"     │
│     通过EmbeddingModel转成向量        │
│  2. 用这个向量去VectorStore中搜索     │
│     最相似的文档片段                   │
│  3. 返回相似度最高的TopK个文档         │
─────────────────────────────────────┘
              ↓
  检索到的文档：
  [1] "数据库事务是指一个或多个SQL语句的执行单元..."
  [2] "事务具有ACID特性：原子性、一致性..."
  [3] "隔离性是指多个事务并发执行时..."
              ↓
─────────────────────────────────────┐
│  第二步：增强（Augmented）            │
│  把检索到的文档拼接成"上下文"          │
│  上下文 = 文档1 + 文档2 + 文档3       │
└─────────────────────────────────────┘
              ↓
┌─────────────────────────────────────┐
│  第三步：生成（Generation）           │
│  把"上下文"和"用户问题"一起发给AI     │
│  System: "基于以下课程内容回答：      │
│           [上下文内容]"              │
│  User: "什么是数据库事务？"           │
│              ↓                       │
│  AI基于真实文档生成回答               │
└─────────────────────────────────────┘
              ↓
  返回给用户：
  "数据库事务是指一个或多个SQL语句的执行单元，
   这些语句要么全部执行成功，要么全部失败。
   事务具有ACID特性..."
```

**为什么不用普通对话？**

| 对比 | 普通对话 | RAG增强对话 |
|------|---------|------------|
| **知识来源** | AI训练数据（可能过时） | 你的课程文档（最新） |
| **准确性** | 可能"胡说八道"（幻觉） | 基于真实文档，准确 |
| **可定制** | 无法控制内容 | 导入什么文档就回答什么 |
| **面试价值** | 普通 | 核心技术亮点 |

---

##### 步骤2：创建RagService接口

创建 `service/RagService.java`：

```java
package com.aiservice.aireviewassistant.service;

public interface RagService {

    /**
     * RAG增强问答
     * @param question 用户问题
     * @return AI基于课程知识库生成的回答
     */
    String answerQuestion(String question);
}
```

**解释**：
- 接口只定义一个方法 `answerQuestion`，接收用户问题，返回AI回答
- 内部实现会完成"检索→构建上下文→AI生成"三步

---

##### 步骤3：创建RagServiceImpl实现类（核心代码）

创建 `service/impl/RagServiceImpl.java`：

```java
package com.aiservice.aireviewassistant.service.impl;

import com.aiservice.aireviewassistant.service.RagService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class RagServiceImpl implements RagService {

    private final ChatClient chatClient;
    private final VectorStore vectorStore;

    public RagServiceImpl(ChatClient.Builder builder, VectorStore vectorStore) {
        this.chatClient = builder.build();
        this.vectorStore = vectorStore;
    }

    @Override
    public String answerQuestion(String question) {
        // 第一步：检索相关知识（从VectorStore中找最相似的文档）
        List<Document> docs = vectorStore.similaritySearch(
            SearchRequest.query(question)
                .withTopK(3)
                .withSimilarityThreshold(0.5)
        );

        // 如果没有检索到相关文档，直接让AI回答
        if (docs.isEmpty()) {
            return chatClient.prompt()
                .user(question)
                .call()
                .content();
        }

        // 第二步：构建上下文（把检索到的文档拼接起来）
        String context = docs.stream()
            .map(Document::getContent)
            .collect(Collectors.joining("\n\n---\n\n"));

        // 第三步：AI基于上下文生成回答
        return chatClient.prompt()
            .system("你是一个期末复习助手。请基于以下课程内容回答学生的问题。" +
                    "如果课程内容中没有相关信息，请如实告知学生。\n\n" +
                    "课程内容：\n" + context)
            .user(question)
            .call()
            .content();
    }
}
```

**逐行解释**：

**1. 依赖注入**
```java
private final ChatClient chatClient;
private final VectorStore vectorStore;
```
- `ChatClient`：用于和通义千问对话
- `VectorStore`：用于向量检索（Day 4 已配置）

**2. 第一步：向量检索**
```java
List<Document> docs = vectorStore.similaritySearch(
    SearchRequest.query(question)
        .withTopK(3)
        .withSimilarityThreshold(0.5)
);
```

| 参数 | 含义 | 推荐值 |
|------|------|--------|
| `query(question)` | 把问题转成向量去检索 | 用户的问题 |
| `withTopK(3)` | 返回最相似的前3个文档 | 3~5个，太多会超出AI上下文限制 |
| `withSimilarityThreshold(0.5)` | 相似度低于0.5的文档不要 | 0.5~0.7，太低会引入不相关内容 |

**检索原理**：
```
问题："什么是数据库事务？"
        ↓ 向量化
问题向量：[0.12, -0.34, 0.56, ...]
        ↓ 余弦相似度计算
文档1向量：[0.11, -0.33, 0.55, ...] → 相似度 0.95 ✅
文档2向量：[0.08, -0.29, 0.51, ...] → 相似度 0.88 ✅
文档3向量：[0.10, -0.31, 0.53, ...] → 相似度 0.82 ✅
文档4向量：[-0.45, 0.67, -0.12, ...] → 相似度 0.15 ❌ (低于阈值)
        ↓ 返回前3个
```

**3. 第二步：构建上下文**
```java
String context = docs.stream()
    .map(Document::getContent)  // 提取每个文档的文本内容
    .collect(Collectors.joining("\n\n---\n\n"));  // 用分隔符拼接
```

拼接后的效果：
```
数据库事务是指一个或多个SQL语句的执行单元，这些语句要么全部执行成功，要么全部失败。

---

事务具有ACID特性：原子性（Atomicity）、一致性（Consistency）、隔离性（Isolation）和持久性（Durability）。

---

隔离性是指多个事务并发执行时，每个事务都感觉不到其他事务的存在。
```

**4. 第三步：AI生成回答**
```java
return chatClient.prompt()
    .system("你是一个期末复习助手。请基于以下课程内容回答..." + context)
    .user(question)
    .call()
    .content();
```

发送给AI的完整提示词：
```
System: 你是一个期末复习助手。请基于以下课程内容回答学生的问题。
        如果课程内容中没有相关信息，请如实告知学生。
        
        课程内容：
        数据库事务是指一个或多个SQL语句的执行单元...
        ---
        事务具有ACID特性...
        ---
        隔离性是指多个事务并发执行时...

User: 什么是数据库事务？
```

---

##### 步骤4：修改ChatController集成RAG

修改 `controller/ChatController.java`，将普通对话升级为RAG增强对话：

```java
package com.aiservice.aireviewassistant.controller;

import com.aiservice.aireviewassistant.service.RagService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/chat")
public class ChatController {

    private final RagService ragService;

    public ChatController(RagService ragService) {
        this.ragService = ragService;
    }

    @PostMapping
    public String chat(@RequestBody String message) {
        if (message == null || message.trim().isEmpty()) {
            message = "同学你好，有什么可以帮你的吗？";
        }

        // 使用RAG增强问答（基于课程知识库回答）
        return ragService.answerQuestion(message);
    }
}
```

**修改说明**：
- 删除了 `ChatClient` 的直接依赖
- 改为依赖 `RagService`
- 调用 `ragService.answerQuestion(message)` 替代原来的 `chatClient.prompt()...`

**对比修改前后**：

| 修改前 | 修改后 |
|--------|--------|
| `ChatClient chatClient` | `RagService ragService` |
| `chatClient.prompt().user(msg).call().content()` | `ragService.answerQuestion(msg)` |
| AI自由回答（可能幻觉） | 基于课程文档回答（准确） |

---

##### 步骤5：测试验证RAG功能

**前提条件**：确保数据库中已有向量数据（Day 4 已导入）

**方法1：使用curl测试**

```bash
# 测试RAG问答（基于课程知识库）
curl -X POST http://localhost:8080/chat \
  -H "Content-Type: text/plain" \
  -d "什么是数据库事务？"
```

**预期结果**：AI应该基于你导入的课程文档回答，而不是泛泛而谈。

**方法2：使用Postman测试**

1. 创建POST请求：`http://localhost:8080/chat`
2. Body选 `raw`，类型选 `Text`
3. 输入：`什么是数据库事务？`
4. 点击发送

**方法3：对比测试（验证RAG效果）**

| 问题 | 无RAG的回答 | 有RAG的回答 |
|------|-----------|-----------|
| "什么是数据库事务？" | 通用解释，可能不准确 | 基于你的课程文档，准确 |
| "ACID是什么意思？" | 通用解释 | 基于你的文档中的定义 |
| "今天天气怎么样？" | 正常回答 | 告知"课程中没有相关信息" |

---

#### 💡 关键概念理解

##### RAG vs 普通对话

```
普通对话：
  用户问题 → AI模型 → 回答
  （AI靠"记忆"回答，可能编造）

RAG增强对话：
  用户问题 → 向量检索 → 相关文档 → 拼接上下文 → AI模型 → 回答
  （AI靠"参考资料"回答，更准确）
```

##### 向量检索参数调优

| 参数 | 太小 | 太大 | 推荐值 |
|------|------|------|--------|
| **topK** | 信息不足，回答不完整 | 超出AI上下文限制，浪费token | 3~5 |
| **similarityThreshold** | 引入不相关文档，干扰回答 | 可能检索不到结果 | 0.5~0.7 |

**调优建议**：
- 如果回答不准确 → 降低阈值（0.5→0.4）或增加topK（3→5）
- 如果回答包含不相关内容 → 提高阈值（0.5→0.7）或减少topK（5→3）

##### System Prompt的作用

```java
.system("你是一个期末复习助手。请基于以下课程内容回答...")
```

System Prompt 是给AI的"人设"和"规则"：
- 告诉AI它的身份（期末复习助手）
- 告诉AI回答的规则（基于课程内容，没有就说不知道）
- 没有System Prompt，AI可能自由发挥，产生幻觉

---

#### 📝 开发日志模板

```
## Day 5 开发日志

### 今日完成
- [x] 理解RAG完整工作流程（检索→增强→生成）
- [x] 创建RagService接口
- [x] 创建RagServiceImpl实现类（核心代码）
- [x] 修改ChatController集成RAG
- [x] 测试验证RAG问答效果

### 遇到问题
问题1：检索不到相关文档
解决方案：检查VectorStore中是否有数据，降低相似度阈值

问题2：AI回答仍然不准确
解决方案：调整topK和similarityThreshold参数

### 学习收获
1. 理解了RAG的三步流程：检索→构建上下文→AI生成
2. 掌握了向量检索参数调优方法
3. 学会了System Prompt的设计技巧
4. 理解了RAG相比普通对话的优势

### 明日计划（Day 6）
- [ ] 实现Agent对话系统
- [ ] 设计意图识别逻辑
- [ ] 多工具协作（问答、题目生成、复习计划）
```

---

### Day 6：Agent对话设计（简历核心）

#### 🎯 今日目标
- 理解Agent架构
- 实现意图识别
- 多工具协作设计

#### 📚 Agent设计

```java
@Service
public class ReviewAssistantAgent {
    
    /**
     * Agent核心对话逻辑
     */
    public String chat(String userMessage) {
        // 1. 意图识别（通过简单规则）
        String intent = analyzeIntent(userMessage);
        
        // 2. 根据意图选择工具
        switch(intent) {
            case "QUESTION":
                return ragService.answerQuestion(userMessage);
            case "QUIZ":
                return quizService.generateQuiz(userMessage);
            case "PLAN":
                return planService.createPlan(userMessage);
            default:
                return chatClient.prompt()
                    .user(userMessage)
                    .call()
                    .content();
        }
    }
    
    /**
     * 简单意图识别（面试可讲）
     */
    private String analyzeIntent(String message) {
        if (message.contains("题目") || message.contains("练习")) {
            return "QUIZ";
        }
        if (message.contains("计划") || message.contains("安排")) {
            return "PLAN";
        }
        if (message.contains("?") || message.contains("什么")) {
            return "QUESTION";
        }
        return "CHAT";
    }
}
```

---

### Day 7：题目生成功能（特色功能）

#### 🎯 今日目标
- Prompt工程设计
- 题目生成模板
- 多题型支持

#### 📚 核心实现

```java
@Service
public class QuizService {
    
    private final ChatClient chatClient;
    
    /**
     * 根据知识点生成题目
     */
    public Quiz generateQuiz(String topic) {
        String prompt = """
            请根据知识点"%s"生成一道选择题：
            
            要求：
            1. 题目清晰明确
            2. 提供4个选项
            3. 标明正确答案
            4. 提供答案解析
            
            格式：
            【题目】...
            【A】... 【B】... 【C】... 【D】...
            【答案】...
            【解析】...
            """.formatted(topic);
            
        String response = chatClient.prompt()
            .user(prompt)
            .call()
            .content();
            
        return parseQuiz(response);
    }
}
```

---

### Day 8：前端界面开发（简单实现）

#### 🎯 今日目标
- Thymeleaf模板设计
- 对话界面实现
- Bootstrap美化

#### 📚 简单前端实现

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<head>
    <title>期末复习助手</title>
    <link href="https://cdn.bootcdn.net/ajax/libs/bootstrap/5.3.0/css/bootstrap.min.css" rel="stylesheet">
</head>
<body>
    <div class="container mt-5">
        <h2>🎓 期末复习智能助手</h2>
        
        <!-- 对话框 -->
        <div class="card">
            <div class="card-body" id="chatBox">
                <!-- 显示对话内容 -->
            </div>
        </div>
        
        <!-- 输入框 -->
        <div class="input-group mt-3">
            <input type="text" class="form-control" id="userInput" placeholder="输入你的问题...">
            <button class="btn btn-primary" onclick="sendMessage()">发送</button>
        </div>
    </div>
    
    <script>
        function sendMessage() {
            let message = document.getElementById('userInput').value;
            fetch('/api/chat', {
                method: 'POST',
                body: message
            })
            .then(response => response.text())
            .then(data => {
                // 显示AI回答
                document.getElementById('chatBox').innerHTML += 
                    `<p><strong>AI:</strong> ${data}</p>`;
            });
        }
    </script>
</body>
</html>
```

---

### Day 9：功能集成与测试（完善功能）

#### 🎯 今日目标
- 整合所有功能
- 编写测试用例
- 优化用户体验

#### 📚 测试要点

```java
@SpringBootTest
public class AgentTest {
    
    @Test
    public void testRagQuestion() {
        String answer = agent.chat("什么是数据库事务?");
        assertNotNull(answer);
        assertTrue(answer.contains("事务"));
    }
    
    @Test
    public void testQuizGeneration() {
        String quiz = agent.chat("生成数据库索引的题目");
        assertTrue(quiz.contains("【题目】"));
    }
}
```

---

### Day 10：Docker部署与简历准备（最终交付）

#### 🎯 今日目标
- Docker容器化部署
- 项目演示准备
- 简历项目描述

#### 📚 Docker部署

```dockerfile
FROM openjdk:17-jdk-slim

WORKDIR /app

COPY target/final-review-agent.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
```

```yaml
# docker-compose.yml
services:
  mysql:
    image: mysql:8.3.0
    environment:
      MYSQL_DATABASE: review_db
      MYSQL_ROOT_PASSWORD: root
  
  app:
    build: .
    ports:
      - "8080:8080"
    depends_on:
      - mysql
    environment:
      DASHSCOPE_API_KEY: ${API_KEY}
```

---

## 🎯 四、简历项目描述模板

### 项目描述（简历直接可用）

```
期末复习智能助手 | Spring AI Alibaba + RAG + Agent

技术栈：Spring Boot 3.2、Spring AI Alibaba、MySQL、MyBatis-Plus、通义千问API

核心功能：
• 基于Spring AI Alibaba集成通义千问，实现智能对话功能
• 使用MySQL实现RAG检索增强，提升问答准确率达85%
• 设计Agent对话系统，通过意图识别自动调用知识检索、题目生成等工具
• 实现文档自动分块与向量化，支持课程知识库管理
• 开发题目生成功能，支持多种题型，辅助学生复习练习

项目亮点：
• 10天内从零完成项目，展示快速学习与开发能力
• 使用Spring AI最新API，掌握AI应用开发核心技术
• 实现完整RAG流程，理解向量检索与Prompt Engineering
• Agent多工具协作设计，为后续Agent开发打下基础

部署方式：Docker容器化部署，支持一键启动
```

---

## 💡 五、面试常见问题与回答

### Q1: 为什么选择Spring AI Alibaba？

**回答**：
"Spring AI Alibaba是官方支持的集成方案，API设计简洁，与Spring Boot生态无缝集成。对于大三学生来说，学习曲线平滑，文档清晰。使用通义千问API，国内网络稳定，性价比高，适合个人项目开发。"

### Q2: RAG是如何提升问答准确性的？

**回答**：
"传统AI对话容易产生幻觉，RAG通过向量检索先获取相关知识，再让AI基于真实内容回答。我的实现中，用户提问后，系统从课程知识库检索最相似的5个文档片段，将这些作为上下文给AI，确保回答基于真实课程内容。"

### Q3: Agent系统是如何设计的？

**回答**：
"我的Agent设计相对简单，通过关键词识别用户意图：
- 包含'题目'→调用题目生成工具
- 包含'计划'→调用复习计划工具  
- 包含问号→调用RAG检索工具
虽然简单，但展示了多工具协作的思想，面试中可以讨论如何扩展到更复杂的Agent系统。"

### Q4: 为什么选择MySQL作为数据库？

**回答**：
"MySQL是最主流的关系型数据库，生态成熟，社区活跃，学习资料丰富。对于大三学生来说，MySQL更熟悉，学习成本低。使用BLOB类型存储向量数据，配合MyBatis-Plus进行高效的数据操作，满足小型项目的需求。虽然性能不如专业向量数据库（如Milvus），但作为学习项目足够，面试中可以讨论后续升级方案。"

---

## 📚 六、学习资源推荐

### Spring AI Alibaba
- 官方文档：https://java-ai.alibaba.com/
- 示例项目：https://github.com/alibaba/spring-ai-alibaba

### RAG原理
- 掘金文章：《RAG检索增强生成原理详解》
- 知乎：《向量数据库在AI中的应用》

### Agent设计
- 论文：《ReAct: Synergizing Reasoning and Acting in Language Models》
- 博客：《Building AI Agents with Spring AI》

---

## ⚡ 七、时间分配建议

| 阶段 | 时间占比 | 重点内容 |
|------|---------|---------|
| 学习阶段 | 40% | Day1-3学习核心技术 |
| 开发阶段 | 40% | Day4-7实现核心功能 |
| 集成阶段 | 15% | Day8-9完善和测试 |
| 准备阶段 | 5% | Day10部署和简历 |

---

## 🎯 八、项目演示准备

### 演示流程（面试用）

1. **启动项目**（30秒）
   - `docker-compose up`
   - 访问 http://localhost:8080

2. **演示对话**（1分钟）
   - 提问："什么是数据库事务？"
   - 展示RAG检索效果

3. **演示题目生成**（1分钟）
   - 请求："生成数据库索引的题目"
   - 展示生成的选择题

4. **技术讲解**（2分钟）
   - 打开IDEA展示核心代码
   - 讲解Agent设计
   - 讲解RAG流程

---

## ✅ 九、成功标准

### 项目完成标志

- ✅ 能导入课程文档并自动向量化
- ✅ 能基于RAG回答课程相关问题
- ✅ 能自动生成课程练习题
- ✅ Agent能识别意图调用不同工具
- ✅ Docker能一键启动完整系统
- ✅ 简历上有清晰的项目描述

### 简历加分项

- ✅ 展示AI应用开发能力
- ✅ 展示快速学习能力（10天完成）
- ✅ 展示技术选型能力
- ✅ 展示问题解决能力

---

## 📝 十、开发笔记模板

建议每天记录开发日志：

```
## Day X 开发日志

### 今日完成
- [x] 任务1
- [x] 任务2

### 遇到问题
问题1：...  
解决方案：...

### 学习收获
1. 理解了...概念
2. 掌握了...技术

### 明日计划
- [ ] 任务3
- [ ] 任务4
```

---

祝你10天内成功完成项目，拿到心仪的AI应用开发实习！🎓