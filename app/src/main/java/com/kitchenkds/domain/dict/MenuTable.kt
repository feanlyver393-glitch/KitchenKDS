package com.kitchenkds.domain.dict

import com.kitchenkds.domain.model.ItemCategory.BEVERAGE
import com.kitchenkds.domain.model.ItemCategory.FOOD

/**
 * 全部词条来源：韩国菜单翻译.txt（139 行，5 个板块，约 96 条去重后词条）。
 *
 * 铁律：分类只认这张表的 category 字段，绝不按名字里带不带 "주/차" 猜。
 *       "녹차/빙홍차" 是茶饮 → BEVERAGE；"고량주/이과두주" 是酒 → BEVERAGE。
 *
 * 新增菜品只需在对应清单里追加一行，解析层 / UI 层无需任何改动。
 */
object MenuTable {

    /** 酒水白名单：20 条，与需求给定的 BEVERAGE 清单完全一致 */
    val BEVERAGES: List<MenuEntry> = listOf(
        MenuEntry("참소주", "魅力烧酒", BEVERAGE),
        MenuEntry("참이슬", "真露烧酒", BEVERAGE),
        MenuEntry("테라", "Terra啤酒", BEVERAGE),
        MenuEntry("카스", "Cass啤酒", BEVERAGE),
        MenuEntry("진로", "真露", BEVERAGE),
        MenuEntry("칭다오", "青岛啤酒", BEVERAGE),
        MenuEntry("타이거맥주", "老虎啤酒", BEVERAGE),
        MenuEntry("이과두주", "二锅头", BEVERAGE),
        MenuEntry("고량주", "高粱酒", BEVERAGE),
        MenuEntry("공부가주(대/중/소)", "孔府家酒", BEVERAGE),
        MenuEntry("연태고량주(대/중/소)", "烟台古酿", BEVERAGE),
        MenuEntry("금문고량주(대/소)", "金门高粱酒", BEVERAGE),
        MenuEntry("죽엽청주(대/중/소)", "竹叶青酒", BEVERAGE),
        MenuEntry("노주탄술", "老酒坛", BEVERAGE),
        MenuEntry("설원(대/소)", "雪原", BEVERAGE),
        MenuEntry("콜라", "可乐", BEVERAGE),
        // 【V4.3】零度可乐三種写法（POS 菜单里三个都在用）
        MenuEntry("제로콜라", "零度可乐", BEVERAGE),
        MenuEntry("코카콜라제로", "零度可乐", BEVERAGE),
        MenuEntry("콜라제로", "零度可乐", BEVERAGE),
        MenuEntry("스프라이트", "雪碧", BEVERAGE),
        MenuEntry("환타", "芬达", BEVERAGE),
        MenuEntry("녹차", "绿茶", BEVERAGE),
        MenuEntry("빙홍차", "冰红茶", BEVERAGE)
    )

    /** 菜品：第一 ~ 第四部分全量 */
    val FOODS: List<MenuEntry> = listOf(
        // ── 第一部分：烤串与套餐 ──
        // 【V4.3】POS 实际打印的完整写法（含 250g 规格，折行合并后就是这一串）。
        // 放在基础名之前：变体索引里 "명품양갈비" 仍映射到基础词条，老行为不变。
        MenuEntry("명품양갈비(250g)", "精品羊排(250g)", FOOD),
        MenuEntry("명품양갈비", "精品羊排", FOOD),
        MenuEntry("양갈비꼬치", "羊排肉串", FOOD),
        MenuEntry("특미양꼬치", "特味羊肉串", FOOD),
        MenuEntry("왕새우꼬치", "大虾串", FOOD),
        MenuEntry("오징어꼬치", "鱿鱼串", FOOD),
        MenuEntry("생양꼬치", "生羊肉串", FOOD),
        MenuEntry("생소꼬치", "生牛肉串", FOOD),
        // 【V4.3】折行合并后的完整写法：SET A(특미+갈비꼬 / 치+꿔바로우)
        // 同样排在旧写法之前，保证 "SET A" 这类简称的译文不被改写。
        MenuEntry("SET A(특미+갈비꼬치+꿔바로우)", "套餐A(特味肉串+羊排肉串+锅包肉)", FOOD),
        MenuEntry("SET A(꼬치+갈비살+꿔)", "套餐A(特味肉串+羊排肉串+锅包肉)", FOOD),
        MenuEntry("SET B(양갈비2+마파두)", "套餐B(精品羊排2份+麻婆豆腐)", FOOD),

        // ── 第二部分：火锅与配菜 ──
        MenuEntry("홍운원앙훠궈", "鸿运鸳鸯火锅", FOOD),
        MenuEntry("양고기", "羊肉", FOOD),
        MenuEntry("소고기", "牛肉", FOOD),
        MenuEntry("왕새우", "大虾", FOOD),
        MenuEntry("키조개", "牛角蛤", FOOD),
        MenuEntry("굴", "生蚝", FOOD),
        MenuEntry("완자", "丸子", FOOD),
        MenuEntry("모듬야채", "蔬菜拼盘", FOOD),
        MenuEntry("버섯", "蘑菇", FOOD),
        MenuEntry("감자", "土豆", FOOD),
        MenuEntry("고구마", "红薯", FOOD),
        MenuEntry("중국당면", "中国宽粉", FOOD),
        MenuEntry("부추", "韭菜", FOOD),
        MenuEntry("두부", "豆腐", FOOD),
        MenuEntry("물만두", "水饺", FOOD),
        MenuEntry("칼국수", "手工面", FOOD),
        MenuEntry("소스", "蘸料", FOOD),

        // ── 第三部分：主食与面类 ──
        MenuEntry("짜장면", "炸酱面", FOOD),
        MenuEntry("짬뽕", "海鲜面", FOOD),
        MenuEntry("볶음밥", "炒饭", FOOD),
        MenuEntry("중국식볶음면", "中式炒面", FOOD),
        MenuEntry("온면", "温面", FOOD),
        MenuEntry("김치볶음밥", "辣白菜炒饭", FOOD),
        MenuEntry("군만두", "煎饺", FOOD),
        MenuEntry("꽃빵", "花卷", FOOD),
        MenuEntry("공기밥", "米饭", FOOD),
        MenuEntry("물냉면", "水冷面", FOOD),
        MenuEntry("비빔냉면", "拌冷面", FOOD),
        MenuEntry("마라탕", "麻辣烫", FOOD),
        // V2：식사 是有效菜品（就餐/加餐），V1 误把它当修饰词丢掉，这里补回词条
        MenuEntry("식사", "就餐", FOOD),

        // ── 第四部分：中式料理 / 热菜 ──
        MenuEntry("탕수육(대/중)", "糖醋肉", FOOD),
        MenuEntry("깐소새우(대/중)", "干烧虾仁", FOOD),
        MenuEntry("깐풍기(대/소)", "干烹鸡", FOOD),
        MenuEntry("고추잡채(대/중)", "青椒肉丝", FOOD),
        MenuEntry("류산슬(대/소)", "溜三丝", FOOD),
        MenuEntry("양장피(대/중)", "两张皮", FOOD),
        MenuEntry("팔보채(대/중)", "八宝菜", FOOD),
        MenuEntry("마라샹궈", "麻辣香锅", FOOD),
        MenuEntry("홍소육", "红烧肉", FOOD),
        MenuEntry("꿔바로우", "锅包肉", FOOD),
        MenuEntry("마라탕(1인분)", "麻辣烫(1人份)", FOOD),
        MenuEntry("왕족발찜", "酱猪蹄", FOOD),
        MenuEntry("수주육편", "水煮肉片", FOOD),
        MenuEntry("어향육슬", "鱼香肉丝", FOOD),
        MenuEntry("경장육슬", "京酱肉丝", FOOD),
        MenuEntry("향라육슬", "香辣肉丝", FOOD),
        MenuEntry("마라쇼롱샤", "麻辣小龙虾", FOOD),
        MenuEntry("막창볶음", "溜肥肠", FOOD),
        MenuEntry("향라새우", "香辣大虾", FOOD),
        MenuEntry("계란토마토볶음", "番茄炒蛋", FOOD),
        MenuEntry("깐궈막창", "干锅肥肠", FOOD),
        MenuEntry("새우튀김", "炸虾仁", FOOD),
        MenuEntry("가지튀김", "炸茄盒", FOOD),
        MenuEntry("깐궈감자", "干锅土豆", FOOD),
        MenuEntry("고추건두부볶음", "尖椒干豆腐", FOOD),
        MenuEntry("마파두부", "麻婆豆腐", FOOD),
        MenuEntry("옥수수튀김", "玉米烙", FOOD),
        MenuEntry("지삼선", "地三鲜", FOOD),
        MenuEntry("가지볶음", "烧茄子", FOOD),
        MenuEntry("어향가지", "鱼香茄子", FOOD),
        MenuEntry("즈란오돌뼈", "孜然月牙骨", FOOD),
        MenuEntry("백김치당면볶음", "酸菜粉条", FOOD),
        MenuEntry("건두부무침", "凉拌干豆腐丝", FOOD),
        MenuEntry("삼선누룽지", "三鲜锅巴汤", FOOD),
        MenuEntry("향라오징어", "香辣鱿鱼", FOOD),
        MenuEntry("건고추소고기볶음", "干辣椒炒牛肉", FOOD),

        // ── 第四部分续：汤 / 凉菜 / 面 ──
        MenuEntry("계란토마토탕", "番茄鸡蛋汤", FOOD),
        MenuEntry("짬뽕탕", "海鲜辣汤", FOOD),
        MenuEntry("고향냉채", "家乡凉菜", FOOD),
        MenuEntry("오이양장피", "黄瓜拉皮", FOOD),
        // 【V4.3】凉菜补词
        MenuEntry("오이무침", "凉拌黄瓜", FOOD),
        MenuEntry("오향장육(한우)", "五香酱肉(韩牛)", FOOD),
        MenuEntry("우소가지", "肉段茄子", FOOD),
        MenuEntry("유육단", "溜肉段", FOOD),
        MenuEntry("우육탕면", "牛肉汤面", FOOD),
        MenuEntry("홍소우육면", "红烧牛肉面", FOOD),
        MenuEntry("마라우육면", "麻辣牛肉面", FOOD),
        MenuEntry("해파리오향장육", "海蜇五香酱肉", FOOD),
        MenuEntry("홍소족발", "红烧猪蹄", FOOD)
    )

    val ALL: List<MenuEntry> = FOODS + BEVERAGES

    /**
     * 修饰词白名单：不是菜品，识别后整行丢弃，不能污染订单。
     * 곱배기 = 加倍（是上一个菜的数量修饰，不单独成行）
     *
     * V2 修正：식사 已从本列表移除 —— 它是有效菜品，必须上屏。
     */
    val IGNORED_TOKENS: Set<String> = setOf("곱배기")
}
