package com.hiringai.mobile.benchmark

import android.content.Context

object StandardTestDataset {

    data class TestCase(
        val id: String,
        val category: String,
        val input: String,
        val expectedOutput: String?,
        val difficulty: Difficulty,
        val description: String
    )

    data class TestSuite(
        val name: String,
        val category: String,
        val description: String,
        val cases: List<TestCase>,
        val version: String
    )

    enum class Difficulty {
        EASY,
        MEDIUM,
        HARD
    }

    fun getLLMTestSuite(): TestSuite {
        val cases = listOf(
            TestCase(
                id = "llm-001",
                category = "LLM",
                input = "你好，介绍一下你自己",
                expectedOutput = null,
                difficulty = Difficulty.EASY,
                description = "简单问候"
            ),
            TestCase(
                id = "llm-002",
                category = "LLM",
                input = "用简洁的语言解释什么是人工智能",
                expectedOutput = null,
                difficulty = Difficulty.EASY,
                description = "概念解释"
            ),
            TestCase(
                id = "llm-003",
                category = "LLM",
                input = "写一段关于春天的诗句",
                expectedOutput = null,
                difficulty = Difficulty.MEDIUM,
                description = "创意写作"
            ),
            TestCase(
                id = "llm-004",
                category = "LLM",
                input = "如果今天是星期一，那么三天后是星期几？",
                expectedOutput = "星期四",
                difficulty = Difficulty.EASY,
                description = "简单推理"
            ),
            TestCase(
                id = "llm-005",
                category = "LLM",
                input = "解释量子计算的基本原理",
                expectedOutput = null,
                difficulty = Difficulty.HARD,
                description = "技术解释"
            ),
            TestCase(
                id = "llm-006",
                category = "LLM",
                input = "翻译：Hello World 到中文",
                expectedOutput = "你好世界",
                difficulty = Difficulty.EASY,
                description = "简单翻译"
            ),
            TestCase(
                id = "llm-007",
                category = "LLM",
                input = "分析以下句子的语法结构：\"我昨天去了公园\"",
                expectedOutput = null,
                difficulty = Difficulty.MEDIUM,
                description = "语法分析"
            ),
            TestCase(
                id = "llm-008",
                category = "LLM",
                input = "给出解决数学问题的步骤：2x + 5 = 15",
                expectedOutput = "x = 5",
                difficulty = Difficulty.MEDIUM,
                description = "数学问题"
            ),
            TestCase(
                id = "llm-009",
                category = "LLM",
                input = "写一封商务邮件邀请客户参加会议",
                expectedOutput = null,
                difficulty = Difficulty.MEDIUM,
                description = "商务写作"
            ),
            TestCase(
                id = "llm-010",
                category = "LLM",
                input = "比较深度学习和机器学习的区别",
                expectedOutput = null,
                difficulty = Difficulty.HARD,
                description = "概念对比"
            )
        )

        return TestSuite(
            name = "LLM 标准测试集",
            category = "LLM",
            description = "用于评估大语言模型性能的标准测试用例集",
            cases = cases,
            version = "1.0"
        )
    }

    fun getSpeechTestSuite(): TestSuite {
        val cases = listOf(
            TestCase(
                id = "speech-001",
                category = "Speech",
                input = "你好世界",
                expectedOutput = "你好世界",
                difficulty = Difficulty.EASY,
                description = "简单中文"
            ),
            TestCase(
                id = "speech-002",
                category = "Speech",
                input = "人工智能正在改变世界",
                expectedOutput = "人工智能正在改变世界",
                difficulty = Difficulty.EASY,
                description = "短句"
            ),
            TestCase(
                id = "speech-003",
                category = "Speech",
                input = "今天天气怎么样",
                expectedOutput = "今天天气怎么样",
                difficulty = Difficulty.EASY,
                description = "日常问句"
            ),
            TestCase(
                id = "speech-004",
                category = "Speech",
                input = "机器学习是人工智能的一个分支",
                expectedOutput = "机器学习是人工智能的一个分支",
                difficulty = Difficulty.MEDIUM,
                description = "技术术语"
            ),
            TestCase(
                id = "speech-005",
                category = "Speech",
                input = "北京是中国的首都",
                expectedOutput = "北京是中国的首都",
                difficulty = Difficulty.EASY,
                description = "常识陈述"
            ),
            TestCase(
                id = "speech-006",
                category = "Speech",
                input = "我喜欢在周末阅读书籍",
                expectedOutput = "我喜欢在周末阅读书籍",
                difficulty = Difficulty.MEDIUM,
                description = "个人陈述"
            ),
            TestCase(
                id = "speech-007",
                category = "Speech",
                input = "自然语言处理是计算机科学的一个领域",
                expectedOutput = "自然语言处理是计算机科学的一个领域",
                difficulty = Difficulty.HARD,
                description = "长句技术内容"
            )
        )

        return TestSuite(
            name = "语音识别测试集",
            category = "Speech",
            description = "用于评估语音识别模型性能的标准测试用例集",
            cases = cases,
            version = "1.0"
        )
    }

    fun getImageTestSuite(): TestSuite {
        val cases = listOf(
            TestCase(
                id = "image-001",
                category = "Image",
                input = "cat",
                expectedOutput = "猫",
                difficulty = Difficulty.EASY,
                description = "常见动物"
            ),
            TestCase(
                id = "image-002",
                category = "Image",
                input = "dog",
                expectedOutput = "狗",
                difficulty = Difficulty.EASY,
                description = "常见动物"
            ),
            TestCase(
                id = "image-003",
                category = "Image",
                input = "car",
                expectedOutput = "汽车",
                difficulty = Difficulty.EASY,
                description = "交通工具"
            ),
            TestCase(
                id = "image-004",
                category = "Image",
                input = "tree",
                expectedOutput = "树",
                difficulty = Difficulty.EASY,
                description = "植物"
            ),
            TestCase(
                id = "image-005",
                category = "Image",
                input = "house",
                expectedOutput = "房子",
                difficulty = Difficulty.EASY,
                description = "建筑物"
            ),
            TestCase(
                id = "image-006",
                category = "Image",
                input = "smartphone",
                expectedOutput = "手机",
                difficulty = Difficulty.MEDIUM,
                description = "电子产品"
            ),
            TestCase(
                id = "image-007",
                category = "Image",
                input = "elephant",
                expectedOutput = "大象",
                difficulty = Difficulty.MEDIUM,
                description = "较大动物"
            ),
            TestCase(
                id = "image-008",
                category = "Image",
                input = "airplane",
                expectedOutput = "飞机",
                difficulty = Difficulty.MEDIUM,
                description = "大型交通工具"
            ),
            TestCase(
                id = "image-009",
                category = "Image",
                input = "mountain",
                expectedOutput = "山",
                difficulty = Difficulty.HARD,
                description = "自然景观"
            ),
            TestCase(
                id = "image-010",
                category = "Image",
                input = "bridge",
                expectedOutput = "桥",
                difficulty = Difficulty.HARD,
                description = "复杂结构"
            )
        )

        return TestSuite(
            name = "图像分类测试集",
            category = "Image",
            description = "用于评估图像分类模型性能的标准测试用例集",
            cases = cases,
            version = "1.0"
        )
    }

    fun getAllTestSuites(): List<TestSuite> {
        return listOf(
            getLLMTestSuite(),
            getSpeechTestSuite(),
            getImageTestSuite()
        )
    }

    fun getTestSuiteByCategory(category: String): TestSuite? {
        return getAllTestSuites().firstOrNull { it.category == category }
    }
}
