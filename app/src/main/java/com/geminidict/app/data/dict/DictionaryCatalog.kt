package com.geminidict.app.data.dict

enum class DictFormat {
    ECDICT_CSV,
    ECDICT_CSV_ZIP,
    WEBSTERS_JSON,
    CC_CEDICT_GZ,
    KAJWEB_JSONL_ZIP,
    FRENCH_JSONL_ZIP,
    CFDICT_TEXT,
    XINHUA_WORD_JSON,
    XINHUA_IDIOM_JSON,
    XINHUA_CI_JSON,
    XINHUA_XIEHOUYU_JSON
}

data class DictionaryCatalogItem(
    val id: String,
    val name: String,
    val categoryLabel: String,
    val description: String,
    val approxSize: String,
    val approxSizeMb: Int,
    val approxEntries: String,
    val license: String,
    val downloadUrl: String,
    val isGithubRaw: Boolean,
    val format: DictFormat,
    /** Non-null for vocabulary books that can be studied directly as wordbooks (e.g. 专八/TOEFL/四级). */
    val wordbookLabel: String? = null
)

object DictionaryCatalog {
    val items: List<DictionaryCatalogItem> = listOf(
        // --- 1. 英英权威公版词典 ---
        DictionaryCatalogItem(
            id = "websters_1913",
            name = "韦氏英英大词典 (Webster's Unabridged 公版)",
            categoryLabel = "英英",
            description = "经典公版韦氏英英大词典（古登堡计划整理），收录 10 万+ 纯正英文释义与词源用法。",
            approxSize = "~22 MB",
            approxSizeMb = 22,
            approxEntries = "约 10.2 万词条",
            license = "Public Domain (WebstersEnglishDictionary)",
            downloadUrl = "https://raw.githubusercontent.com/matthewreagan/WebstersEnglishDictionary/master/dictionary_compact.json",
            isGithubRaw = true,
            format = DictFormat.WEBSTERS_JSON
        ),

        // --- 2. 权威开源英汉大词库（含柯林斯星级 / 牛津3000 标注） ---
        DictionaryCatalogItem(
            id = "ecdict_full",
            name = "ECDICT 标准英汉词典（含柯林斯星级/牛津3000）",
            categoryLabel = "英汉",
            description = "77 万+ 高口碑开源英汉双解词典，标注柯林斯星级、牛津3000核心词、BNC/COCA词频与完整词形变化。",
            approxSize = "~66 MB",
            approxSizeMb = 66,
            approxEntries = "约 77 万词条",
            license = "MIT (skywind3000/ECDICT)",
            downloadUrl = "https://raw.githubusercontent.com/skywind3000/ECDICT/master/ecdict.csv",
            isGithubRaw = true,
            format = DictFormat.ECDICT_CSV
        ),
        DictionaryCatalogItem(
            id = "ecdict_ultimate",
            name = "ECDICT 增强终极版（340万词条豪华英汉库）",
            categoryLabel = "英汉·豪华",
            description = "ECDICT 340 万+ 词条超大英汉词库，覆盖冷门专业词、医学/科技/专有名词与衍生词（ZIP 压缩，支持断点续传）。",
            approxSize = "~94 MB",
            approxSizeMb = 94,
            approxEntries = "约 340 万词条",
            license = "MIT (skywind3000/ECDICT-ultimate)",
            downloadUrl = "https://github.com/skywind3000/ECDICT-ultimate/releases/download/1.0.0/ecdict-ultimate-csv.zip",
            isGithubRaw = true,
            format = DictFormat.ECDICT_CSV_ZIP
        ),

        // --- 3. 含真实双语例句的开源英汉词库（下载后自动出现在「单词本」分组供全词库学习） ---
        DictionaryCatalogItem(
            id = "kajweb_level8",
            name = "专八核心词汇（含双语例句）",
            categoryLabel = "英汉·例句",
            description = "含美/英音标、中英释义与真实中英双语例句，下载后可直接在「单词本」按遗忘曲线学习全部词汇。",
            approxSize = "~5.1 MB",
            approxSizeMb = 5,
            approxEntries = "12,197 词条",
            license = "Open Source (kajweb/dict)",
            downloadUrl = "https://raw.githubusercontent.com/kajweb/dict/master/book/1521164650006_Level8luan_2.zip",
            isGithubRaw = true,
            format = DictFormat.KAJWEB_JSONL_ZIP,
            wordbookLabel = "专八"
        ),
        DictionaryCatalogItem(
            id = "kajweb_toefl",
            name = "TOEFL 核心词汇（含双语例句）",
            categoryLabel = "英汉·例句",
            description = "托福高频词库，每词附带音标、中文释义及双语例句，下载后可作为单词本学习。",
            approxSize = "~4.1 MB",
            approxSizeMb = 4,
            approxEntries = "9,213 词条",
            license = "Open Source (kajweb/dict)",
            downloadUrl = "https://raw.githubusercontent.com/kajweb/dict/master/book/1521164640451_TOEFL_2.zip",
            isGithubRaw = true,
            format = DictFormat.KAJWEB_JSONL_ZIP,
            wordbookLabel = "TOEFL"
        ),
        DictionaryCatalogItem(
            id = "kajweb_gre",
            name = "GRE 核心词汇（含双语例句）",
            categoryLabel = "英汉·例句",
            description = "GRE 高阶学术词汇库，附带音标、精准中文释义及双语例句，下载后可作为单词本学习。",
            approxSize = "~2.4 MB",
            approxSizeMb = 3,
            approxEntries = "7,199 词条",
            license = "Open Source (kajweb/dict)",
            downloadUrl = "https://raw.githubusercontent.com/kajweb/dict/master/book/1521164637271_GRE_2.zip",
            isGithubRaw = true,
            format = DictFormat.KAJWEB_JSONL_ZIP,
            wordbookLabel = "GRE"
        ),
        DictionaryCatalogItem(
            id = "kajweb_kaoyan",
            name = "考研英语词汇（含双语例句）",
            categoryLabel = "英汉·例句",
            description = "考研核心词库，含音标、多词性释义及双语例句，下载后可作为单词本学习。",
            approxSize = "~2.3 MB",
            approxSizeMb = 2,
            approxEntries = "4,533 词条",
            license = "Open Source (kajweb/dict)",
            downloadUrl = "https://raw.githubusercontent.com/kajweb/dict/master/book/1521164654696_KaoYan_2.zip",
            isGithubRaw = true,
            format = DictFormat.KAJWEB_JSONL_ZIP,
            wordbookLabel = "考研"
        ),
        DictionaryCatalogItem(
            id = "kajweb_sat",
            name = "SAT 核心词汇（含双语例句）",
            categoryLabel = "英汉·例句",
            description = "SAT 考试核心词库，含音标与双语例句，下载后可作为单词本学习。",
            approxSize = "~1.7 MB",
            approxSizeMb = 2,
            approxEntries = "4,423 词条",
            license = "Open Source (kajweb/dict)",
            downloadUrl = "https://raw.githubusercontent.com/kajweb/dict/master/book/1521164670910_SAT_2.zip",
            isGithubRaw = true,
            format = DictFormat.KAJWEB_JSONL_ZIP,
            wordbookLabel = "SAT"
        ),
        DictionaryCatalogItem(
            id = "kajweb_cet4",
            name = "四级英语词汇（含双语例句）",
            categoryLabel = "英汉·例句",
            description = "大学英语四级核心词库，含音标、释义与双语例句，下载后可作为单词本学习。",
            approxSize = "~2.6 MB",
            approxSizeMb = 3,
            approxEntries = "3,739 词条",
            license = "Open Source (kajweb/dict)",
            downloadUrl = "https://raw.githubusercontent.com/kajweb/dict/master/book/1524052539052_CET4luan_2.zip",
            isGithubRaw = true,
            format = DictFormat.KAJWEB_JSONL_ZIP,
            wordbookLabel = "四级"
        ),
        DictionaryCatalogItem(
            id = "kajweb_cet6",
            name = "六级英语词汇（含双语例句）",
            categoryLabel = "英汉·例句",
            description = "大学英语六级核心词库，含音标、释义与双语例句，下载后可作为单词本学习。",
            approxSize = "~1.1 MB",
            approxSizeMb = 2,
            approxEntries = "2,078 词条",
            license = "Open Source (kajweb/dict)",
            downloadUrl = "https://raw.githubusercontent.com/kajweb/dict/master/book/1524052554766_CET6_2.zip",
            isGithubRaw = true,
            format = DictFormat.KAJWEB_JSONL_ZIP,
            wordbookLabel = "六级"
        ),
        DictionaryCatalogItem(
            id = "kajweb_ielts",
            name = "雅思核心词汇（含双语例句）",
            categoryLabel = "英汉·例句",
            description = "IELTS 高频词库，含音标、释义与双语例句，下载后可作为单词本学习。",
            approxSize = "~1.5 MB",
            approxSizeMb = 2,
            approxEntries = "3,427 词条",
            license = "Open Source (kajweb/dict)",
            downloadUrl = "https://raw.githubusercontent.com/kajweb/dict/master/book/1521164624473_IELTSluan_2.zip",
            isGithubRaw = true,
            format = DictFormat.KAJWEB_JSONL_ZIP,
            wordbookLabel = "雅思"
        ),
        DictionaryCatalogItem(
            id = "kajweb_bec",
            name = "商务英语 BEC 词汇（含双语例句）",
            categoryLabel = "英汉·例句",
            description = "职场与商务英语核心词汇，含商务场景双语例句，下载后可作为单词本学习。",
            approxSize = "~1.4 MB",
            approxSizeMb = 2,
            approxEntries = "2,753 词条",
            license = "Open Source (kajweb/dict)",
            downloadUrl = "https://raw.githubusercontent.com/kajweb/dict/master/book/1521164626760_BEC_2.zip",
            isGithubRaw = true,
            format = DictFormat.KAJWEB_JSONL_ZIP,
            wordbookLabel = "商务英语"
        ),

        // --- 4. 法汉 / 汉法离线词典（适合中国人初学法语，带双语例句） ---
        DictionaryCatalogItem(
            id = "french_zh",
            name = "法汉词典（初学法语·带双语例句）",
            categoryLabel = "法汉·例句",
            description = "适合中国人初学法语的法汉词典：2.4 万法语词头配中文释义，高频词附带 Tatoeba 真实法-中双语例句（全库约 21% 词条有例句），下载后可直接在「单词本」按遗忘曲线学习全部词汇。",
            approxSize = "~0.5 MB",
            approxSizeMb = 1,
            approxEntries = "23,589 词条",
            license = "CC BY-SA (WikDict) + CC BY 2.0 FR (Tatoeba)",
            downloadUrl = "https://raw.githubusercontent.com/sleepy980wl/GeminiDictAndroid-lite/main/dictionaries/french_zh.jsonl.zip",
            isGithubRaw = true,
            format = DictFormat.FRENCH_JSONL_ZIP,
            wordbookLabel = "法语"
        ),
        DictionaryCatalogItem(
            id = "cfdict",
            name = "汉法词典 CFDICT（中文查法语）",
            categoryLabel = "汉法",
            description = "5.6 万条中文词头查法语释义，带拼音，适合由中文反查法语说法，与法汉词典配合实现中法双向离线查词。",
            approxSize = "~3.5 MB",
            approxSizeMb = 4,
            approxEntries = "56,300 词条",
            license = "CC BY-SA 3.0 (CxDICT/CFDICT)",
            downloadUrl = "https://raw.githubusercontent.com/licryle/CxDICT/main/dictionaries/fr/data/cfdict.u8",
            isGithubRaw = true,
            format = DictFormat.CFDICT_TEXT
        ),

        // --- 5. 汉英开源词典 ---
        DictionaryCatalogItem(
            id = "cc_cedict",
            name = "CC-CEDICT 汉英大词典",
            categoryLabel = "汉英",
            description = "全球最知名的持续维护开源汉英词典，支持简体/繁体中文查英文释义及拼音标注。",
            approxSize = "~4 MB",
            approxSizeMb = 4,
            approxEntries = "约 12.5 万词条",
            license = "CC BY-SA 4.0 (MDBG)",
            downloadUrl = "https://www.mdbg.net/chinese/export/cedict/cedict_1_0_ts_utf-8_mdbg.txt.gz",
            isGithubRaw = false,
            format = DictFormat.CC_CEDICT_GZ
        ),

        // --- 6. 中文新华字典系列 ---
        DictionaryCatalogItem(
            id = "xinhua_word",
            name = "中华新华字典 · 汉字库",
            categoryLabel = "新华字典",
            description = "收录 1.6 万余汉字，含拼音、部首、笔画数、基本释义及古汉语详解。",
            approxSize = "~27 MB",
            approxSizeMb = 27,
            approxEntries = "16,142 汉字",
            license = "MIT (pwxcoo/chinese-xinhua)",
            downloadUrl = "https://raw.githubusercontent.com/pwxcoo/chinese-xinhua/master/data/word.json",
            isGithubRaw = true,
            format = DictFormat.XINHUA_WORD_JSON
        ),
        DictionaryCatalogItem(
            id = "xinhua_idiom",
            name = "中华新华字典 · 成语词典",
            categoryLabel = "新华字典",
            description = "收录 3.1 万余条汉语成语，包含拼音、释义、典故出处与例句。",
            approxSize = "~13 MB",
            approxSizeMb = 13,
            approxEntries = "31,648 成语",
            license = "MIT (pwxcoo/chinese-xinhua)",
            downloadUrl = "https://raw.githubusercontent.com/pwxcoo/chinese-xinhua/master/data/idiom.json",
            isGithubRaw = true,
            format = DictFormat.XINHUA_IDIOM_JSON
        ),
        DictionaryCatalogItem(
            id = "xinhua_ci",
            name = "中华新华字典 · 汉语词语库",
            categoryLabel = "新华字典",
            description = "收录 26 万余条汉语词汇与释义，适合中文词义离线检索。",
            approxSize = "~25 MB",
            approxSizeMb = 25,
            approxEntries = "264,434 词语",
            license = "MIT (pwxcoo/chinese-xinhua)",
            downloadUrl = "https://raw.githubusercontent.com/pwxcoo/chinese-xinhua/master/data/ci.json",
            isGithubRaw = true,
            format = DictFormat.XINHUA_CI_JSON
        ),
        DictionaryCatalogItem(
            id = "xinhua_xiehouyu",
            name = "中华新华字典 · 歇后语库",
            categoryLabel = "新华字典",
            description = "收录 1.4 万余条传统歇后语（谜面与谜底）。",
            approxSize = "~1.5 MB",
            approxSizeMb = 2,
            approxEntries = "14,032 歇后语",
            license = "MIT (pwxcoo/chinese-xinhua)",
            downloadUrl = "https://raw.githubusercontent.com/pwxcoo/chinese-xinhua/master/data/xiehouyu.json",
            isGithubRaw = true,
            format = DictFormat.XINHUA_XIEHOUYU_JSON
        )
    )

    val wordbookBooksByLabel: Map<String, DictionaryCatalogItem> = items
        .filter { it.wordbookLabel != null }
        .associateBy { it.wordbookLabel!! }
}
