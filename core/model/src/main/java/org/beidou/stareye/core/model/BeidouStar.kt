package org.beidou.stareye.core.model

/**
 * 北斗七星（大熊座）—— 自斗身至斗柄的七个星位。
 *
 * 每个功能中枢对应一颗星，承载「北斗·群星之眼」的主题。
 */
enum class BeidouStar(
    /** 中文星名 */
    val chineseName: String,
    /** 西方星名 */
    val englishName: String,
    /** 斗序 1..7，决定界面排布顺序 */
    val order: Int,
) {
    TIANSHU("天枢", "Dubhe", 1),
    TIANXUAN("天璇", "Merak", 2),
    TIANJI("天玑", "Phecda", 3),
    TIANQUAN("天权", "Megrez", 4),
    YUHENG("玉衡", "Alioth", 5),
    KAIYANG("开阳", "Mizar", 6),
    YAOGUANG("瑶光", "Alkaid", 7),
}
