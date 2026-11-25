package com.kitchenkds.domain.dict

import com.kitchenkds.domain.model.ItemCategory

data class MenuEntry(
    /** 词典标准写法（可带尺寸括号，如 "공부가주(대/중/소)"） */
    val korean: String,
    /** 中文名（不含尺寸，尺寸由 unitHint 单独呈现） */
    val chinese: String,
    val category: ItemCategory
)
