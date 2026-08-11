package com.roserequiem.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * "Ink & Amber" — Rose Requiem's color identity.
 *
 * Grounded in what this app actually does: lyrics are text (ink) and finding a match
 * feels like warmth (amber). Deep indigo-ink stands in for the written word; warm gold
 * stands in for the moment a match is found. Used together as a considered pair rather
 * than a single accent on near-black or a stock Material template color.
 */

// Ink (indigo) — primary
val InkLight10 = Color(0xFF2C2650)
val InkLight30 = Color(0xFF4A4370)
val InkContainerLight = Color(0xFFE3DFFF)
val InkDark80 = Color(0xFFC7BFFF)
val InkDark20 = Color(0xFF302A55)
val InkContainerDark = Color(0xFF46406C)

// Amber (gold) — secondary
val AmberLight10 = Color(0xFF2E1B00)
val AmberLight40 = Color(0xFF8A5A1F)
val AmberContainerLight = Color(0xFFFFDDAE)
val AmberDark80 = Color(0xFFF0BB63)
val AmberDark20 = Color(0xFF452C00)
val AmberContainerDark = Color(0xFF63430A)

// Plum — tertiary, a quiet harmonizing accent
val PlumLight10 = Color(0xFF251431)
val PlumLight40 = Color(0xFF6B5778)
val PlumContainerLight = Color(0xFFF0DBFF)
val PlumDark80 = Color(0xFFD6BEE3)
val PlumDark20 = Color(0xFF3B2947)
val PlumContainerDark = Color(0xFF533F5F)

// Neutrals
val SurfaceLight = Color(0xFFFBFAFF)
val OnSurfaceLight = Color(0xFF1B1B21)
val SurfaceVariantLight = Color(0xFFE5E1EC)
val OnSurfaceVariantLight = Color(0xFF47464F)
val OutlineLight = Color(0xFF78767F)

// Deep ink-black with a violet undertone — the signature dark background
val SurfaceDark = Color(0xFF131018)
val OnSurfaceDark = Color(0xFFE5E1E9)
val SurfaceVariantDark = Color(0xFF47464F)
val OnSurfaceVariantDark = Color(0xFFC8C5D0)
val OutlineDark = Color(0xFF928F99)

// Error (kept close to Material convention — no reason to reinvent "something's wrong")
val ErrorLight = Color(0xFFBA1B2C)
val ErrorContainerLight = Color(0xFFFFDAD9)
val OnErrorContainerLight = Color(0xFF410006)
val ErrorDark = Color(0xFFFFB3AF)
val ErrorContainerDark = Color(0xFF930010)
val OnErrorContainerDark = Color(0xFFFFDAD9)