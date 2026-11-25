package com.kitchenkds.domain.model

/**
 * NEW     = [주문서]   新下单，卡片头部用深天蓝
 * REPRINT = [재주문서] 重打/补打，卡片头部用警示黄（后厨必须一眼区分，防止重复出餐）
 * UNKNOWN = 关键字缺失时的兜底，按 NEW 处理但打灰色角标
 */
enum class OrderKind { NEW, REPRINT, UNKNOWN }
