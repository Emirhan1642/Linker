fun main() {
    val methods = com.spotify.android.appremote.api.ConnectionParams.Builder::class.java.methods
    for (m in methods) {
        println(m.name)
    }
}
