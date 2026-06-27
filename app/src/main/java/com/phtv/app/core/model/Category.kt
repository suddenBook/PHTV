package com.phtv.app.core.model

/** A browseable PornHub category. Browse URL is `/video?c=<id>`. */
data class Category(val id: String, val name: String)

/**
 * Static category taxonomy captured from /categories on 2026-06-27 (96 entries).
 * Used as a fast, offline-friendly default; the live /categories page can refresh it later.
 */
object Categories {
    val ALL: List<Category> = listOf(
        Category("1", "Asian"), Category("2", "Orgy"), Category("3", "Amateur"),
        Category("4", "Big Ass"), Category("6", "BBW"), Category("7", "Big Dick"),
        Category("8", "Big Tits"), Category("9", "Blonde"), Category("10", "Bondage"),
        Category("11", "Brunette"), Category("12", "Celebrity"), Category("13", "Blowjob"),
        Category("14", "Bukkake"), Category("15", "Creampie"), Category("16", "Cumshot"),
        Category("17", "Ebony"), Category("18", "Fetish"), Category("19", "Fisting"),
        Category("20", "Handjob"), Category("21", "Hardcore"), Category("22", "Masturbation"),
        Category("23", "Toys"), Category("24", "Public"), Category("25", "Interracial"),
        Category("26", "Latina"), Category("27", "Lesbian"), Category("28", "Mature"),
        Category("29", "MILF"), Category("31", "Reality"), Category("32", "Funny"),
        Category("33", "Striptease"), Category("35", "Anal"), Category("41", "POV"),
        Category("42", "Red Head"), Category("43", "Vintage"), Category("53", "Party"),
        Category("55", "Euro"), Category("57", "Compilation"), Category("59", "Small Tits"),
        Category("61", "Webcam"), Category("65", "Threesome"), Category("67", "Rough Sex"),
        Category("69", "Squirt"), Category("72", "Double Penetration"), Category("76", "Bisexual Male"),
        Category("78", "Massage"), Category("80", "Gangbang"), Category("81", "Role Play"),
        Category("86", "Cartoon"), Category("88", "School (18+)"), Category("89", "Babysitter (18+)"),
        Category("90", "Casting"), Category("91", "Smoking"), Category("92", "Solo Male"),
        Category("93", "Feet"), Category("94", "French"), Category("95", "German"),
        Category("96", "British"), Category("97", "Italian"), Category("98", "Arab"),
        Category("99", "Russian"), Category("100", "Czech"), Category("101", "Indian"),
        Category("102", "Brazilian"), Category("103", "Korean"), Category("105", "60FPS"),
        Category("111", "Japanese"), Category("115", "Exclusive"), Category("121", "Music"),
        Category("131", "Pussy Licking"), Category("138", "Verified Amateurs"),
        Category("139", "Verified Models"), Category("141", "Behind The Scenes"),
        Category("181", "Old/Young (18+)"), Category("201", "Parody"), Category("211", "Pissing"),
        Category("241", "Cosplay"), Category("242", "Cuckold"), Category("444", "Step Fantasy"),
        Category("482", "Verified Couples"), Category("492", "Solo Female"),
        Category("502", "Female Orgasm"), Category("512", "Muscular Men"), Category("522", "Romantic"),
        Category("532", "Scissoring"), Category("542", "Strap On"), Category("562", "Tattooed Women"),
        Category("572", "Trans With Girl"), Category("592", "Fingering"), Category("612", "360°"),
        Category("712", "Uncensored"), Category("722", "Uncensored (JAV)"),
        Category("732", "Audio Impaired"), Category("761", "FFM"), Category("881", "Gaming"),
        Category("891", "Podcast"),
    )
}
