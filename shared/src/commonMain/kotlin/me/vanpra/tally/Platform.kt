package me.vanpra.tally

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform