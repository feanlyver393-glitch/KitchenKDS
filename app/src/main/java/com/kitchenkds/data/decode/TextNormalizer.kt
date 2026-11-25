package com.kitchenkds.data.decode

/**
 * 解码后的字符级清理 + 菜名清洗（V4.3）。
 *
 * 注意：normalize 里绝对不能折叠空格 —— 数量解析依赖「菜名 + 多个空格 + 数字」的对齐排版。
 *
 * V4.3 修正：
 *   ① 剔除 Unicode 替换字符 U+FFFD（即解码失败时打印出来的黑色问号），以及 U+FFFE / U+FFFF；
 *   ② 剔除不可见字符：C0 控制字符 / DEL / 软连字符 / 零宽字符 / 方向标记；
 *   ③ NBSP、全角空格等统一替换成普通空格 —— 只替换不删除，
 *      否则「菜名 + 对齐空格 + 数量」会被粘成一坨，数量解析直接失效。
 */
object TextNormalizer {

    private val zeroWidth = Regex("[\\u200B-\\u200F\\uFEFF\\u2060]")
    private val ctrl = Regex("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F\\u007F]")

    /** V4.3：解码失败占位符（U+FFFD）与非字符码位 —— 一律剔除 */
    private val replacementChars = Regex("[\\uFFFD\\uFFFE\\uFFFF]")

    /** V4.3：软连字符 / 方向标记等不可见字符 */
    private val invisibleMarks = Regex("[\\u00AD\\u200E\\u200F\\u2061-\\u2064]")

    /** V4.3：各种"看起来像空格"的字符 → 普通空格（不删，只替换） */
    private val spaceLike = Regex("[\\u00A0\\u2000-\\u200A\\u202F\\u205F\\u3000]")

    /** 打印机常见的纯装饰行 */
    private val decorLine = Regex("^[\\s\\-=*_.~+·•]{3,}$")

    /**
     * 【V5.1 关键修复】追加状态后缀 —— 只吃「추가」这个词，绝不吃它前面的数量数字！
     *
     * 旧正则 \s*[0-9]*\s*추가$ 会把 "꿔바로우       2 추가" 里的 2 一起删掉，
     * 导致 OrderLineParser 拿不到数量、全部退化成 qty = 1（整单数量全错的严重 bug）。
     *
     * 现在的行为：
     *   "꿔바로우       2 추가"  → "꿔바로우       2"   （数量 2 保留，交给解析器）
     *   "향라오징어추가"          → "향라오징어"
     *   "향라오징어 추가"         → "향라오징어"
     * 只吃行尾，不动中间内容（"추가요리" 这种正常菜名不受影响）。
     */
    private val appendSuffix = Regex("\\s*추\\s*가\\s*$")

    /** 查词典前再兜一层：去掉「空格 + 1~3 位数字」的纯数量尾巴（不影响入库菜名） */
    private val qtyTail = Regex("\\s+\\d{1,3}$")

    /** 清洗后可能残留的连续空格 */
    private val tailSpace = Regex("\\s{2,}")

    fun normalize(raw: String): String {
        var s = raw.replace("\r\n", "\n").replace('\r', '\n')
        // ① 乱码占位符
        s = replacementChars.replace(s, "")
        // ② 不可见字符（软连字符 / 零宽 / 方向标记）
        s = invisibleMarks.replace(s, "")
        s = zeroWidth.replace(s, "")
        // ③ 控制字符
        s = ctrl.replace(s, "")
        // ④ 各种 Unicode 空格 → 普通空格
        s = spaceLike.replace(s, " ")
        return s
    }

    fun isDecorLine(line: String): Boolean = decorLine.matches(line)

    fun squeeze(line: String): String = line.replace(Regex("[ \\t]{2,}"), " ").trim()

    /**
     * 【V4.2】菜名清洗：剔掉行尾的「追加」状态描述，再去查词典。
     * 【V4.3】额外保证：即使调用方没走 normalize，也不会把 U+FFFD 带进词典与数据库。
     * 必须在 MenuDictionary.lookup 之前调用。
     */
    fun cleanDishName(raw: String): String {
        var s = raw.trim()
        s = replacementChars.replace(s, "")
        s = invisibleMarks.replace(s, "")
        s = zeroWidth.replace(s, "")
        s = ctrl.replace(s, "")
        s = spaceLike.replace(s, " ")
        s = appendSuffix.replace(s, "")
        s = tailSpace.replace(s, " ")
        return s.trim()
    }

    /**
     * 词典查询专用：把「菜名 + 空格 + 数量」里的数量摘掉，只留纯菜名。
     * 只影响查词结果，不改变 OrderItem.koreanName（入库名由解析器保证已无数量）。
     *   "꿔바로우 2"          → "꿔바로우"
     *   "명품양갈비(250g) 3"  → "명품양갈비(250g)"   ← 括号里的规格不受影响
     */
    fun stripQuantitySuffix(name: String): String {
        val s = qtyTail.replace(name.trim(), "").trim()
        return s.ifEmpty { name.trim() }
    }
}
