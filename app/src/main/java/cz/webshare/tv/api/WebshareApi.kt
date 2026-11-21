package cz.webshare.tv.api

import cz.webshare.tv.model.WebshareFile
import cz.webshare.tv.model.WebshareUserProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.apache.commons.codec.digest.DigestUtils
import org.apache.commons.codec.digest.Md5Crypt
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.TimeUnit

class WebshareApi(
    private val client: OkHttpClient = defaultHttpClient()
) {
    companion object {
        private const val BASE_URL = "https://webshare.cz/api"

        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    private val deviceUuid: String = UUID.randomUUID().toString()

    /**
     * Authenticates with Webshare.cz using salt -> md5_crypt -> sha1 -> digest flow.
     * Returns the session token (`wst`).
     */
    suspend fun login(usernameOrEmail: String, plainPassword: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val trimmedUser = usernameOrEmail.trim()
                require(trimmedUser.isNotEmpty() && plainPassword.isNotEmpty()) {
                    "Zadej uživatelské jméno (nebo e-mail) a heslo."
                }

                // Step 1: Fetch user salt
                val saltXml = postForm(
                    endpoint = "$BASE_URL/salt/",
                    params = mapOf("username_or_email" to trimmedUser)
                )
                val saltStatus = extractTagValue(saltXml, "status")
                if (saltStatus != "OK") {
                    val msg = extractTagValue(saltXml, "message")
                        ?: "Účet '$trimmedUser' nebyl na Webshare.cz nalezen."
                    throw IllegalStateException(msg)
                }
                val salt = extractTagValue(saltXml, "salt")
                    ?: throw IllegalStateException("Webshare API nevrátilo salt.")

                // Step 2: Compute Webshare password hash: sha1(md5_crypt(password, "$1$" + salt))
                val md5CryptStr = Md5Crypt.md5Crypt(
                    plainPassword.toByteArray(StandardCharsets.UTF_8),
                    "\$1\$$salt"
                )
                val passwordHash = DigestUtils.sha1Hex(md5CryptStr)
                val digest = DigestUtils.md5Hex("$trimmedUser:Webshare:$passwordHash")

                // Step 3: Perform login
                val loginXml = postForm(
                    endpoint = "$BASE_URL/login/",
                    params = mapOf(
                        "username_or_email" to trimmedUser,
                        "password" to passwordHash,
                        "digest" to digest,
                        "keep_logged_in" to "1"
                    )
                )
                val loginStatus = extractTagValue(loginXml, "status")
                if (loginStatus != "OK") {
                    val msg = extractTagValue(loginXml, "message")
                        ?: "Nesprávné jméno nebo heslo k Webshare.cz."
                    throw IllegalStateException(msg)
                }
                extractTagValue(loginXml, "token")
                    ?: throw IllegalStateException("Přihlášení proběhlo, ale chybí session token.")
            }
        }

    /**
     * Retrieves profile & VIP status for an active `wst` token.
     */
    suspend fun getUserProfile(token: String): Result<WebshareUserProfile> =
        withContext(Dispatchers.IO) {
            runCatching {
                val xml = postForm(
                    endpoint = "$BASE_URL/user_data/",
                    params = mapOf("wst" to token)
                )
                val status = extractTagValue(xml, "status")
                if (status != "OK") {
                    throw IllegalStateException("Session token vypršel nebo je neplatný.")
                }
                val username = extractTagValue(xml, "username") ?: "VIP Uživatel"
                val vipFlag = extractTagValue(xml, "vip") == "1"
                val vipDays = extractTagValue(xml, "vip_days") ?: "0"
                val vipUntil = extractTagValue(xml, "vip_until") ?: ""
                WebshareUserProfile(
                    username = username,
                    isVip = vipFlag || (vipDays.toIntOrNull() ?: 0) > 0,
                    vipDaysLeft = vipDays,
                    vipUntil = vipUntil
                )
            }
        }

    /**
     * Searches video files on Webshare.cz (`/api/search/`).
     */
    suspend fun searchVideos(
        query: String,
        token: String,
        sort: String = "largest",
        limit: Int = 80,
        offset: Int = 0
    ): Result<List<WebshareFile>> = withContext(Dispatchers.IO) {
        runCatching {
            val params = mutableMapOf(
                "what" to query.trim(),
                "category" to "video",
                "limit" to limit.toString(),
                "offset" to offset.toString(),
                "wst" to token
            )
            if (sort.isNotBlank()) {
                params["sort"] = sort
            }
            val xml = postForm(
                endpoint = "$BASE_URL/search/",
                params = params
            )
            val status = extractTagValue(xml, "status")
            if (status != "OK") {
                val msg = extractTagValue(xml, "message") ?: "Chyba při vyhledávání na Webshare."
                throw IllegalStateException(msg)
            }
            parseFileListXml(xml)
        }
    }

    /**
     * Resolves a file `ident` into a direct VIP HTTPS video stream link (`/api/file_link/`).
     */
    suspend fun getDirectStreamUrl(ident: String, token: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val xml = postForm(
                    endpoint = "$BASE_URL/file_link/",
                    params = mapOf(
                        "ident" to ident,
                        "wst" to token,
                        "download_type" to "video_stream",
                        "device_uuid" to deviceUuid,
                        "force_https" to "1"
                    )
                )
                val status = extractTagValue(xml, "status")
                if (status != "OK") {
                    val msg = extractTagValue(xml, "message")
                        ?: "Nepodařilo se získat VIP stream z Webshare."
                    throw IllegalStateException(msg)
                }
                val link = extractTagValue(xml, "link")
                    ?: throw IllegalStateException("Webshare nevrátil streamovací odkaz.")
                link.trim()
            }
        }

    private fun postForm(endpoint: String, params: Map<String, String>): String {
        val formBuilder = FormBody.Builder(StandardCharsets.UTF_8)
        for ((k, v) in params) {
            formBuilder.add(k, v)
        }
        val request = Request.Builder()
            .url(endpoint)
            .header("Accept", "text/xml; charset=UTF-8")
            .header("User-Agent", "WebshareTV/1.0 (Android TV; Media3)")
            .post(formBuilder.build())
            .build()

        client.newCall(request).execute().use { response ->
            val bodyStr = response.body?.string().orEmpty()
            if (!response.isSuccessful && bodyStr.isEmpty()) {
                throw IllegalStateException("HTTP chyba ${response.code} při komunikaci s Webshare.cz")
            }
            return bodyStr
        }
    }

    private fun extractTagValue(xml: String, tagName: String): String? {
        return try {
            val factory = XmlPullParserFactory.newInstance()
            val parser = factory.newPullParser()
            parser.setInput(StringReader(xml))
            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG && parser.name == tagName) {
                    return parser.nextText()?.trim()
                }
                eventType = parser.next()
            }
            null
        } catch (_: Exception) {
            val regex = Regex("<$tagName>([\\s\\S]*?)</$tagName>")
            regex.find(xml)?.groupValues?.getOrNull(1)?.trim()
        }
    }

    private fun parseFileListXml(xml: String): List<WebshareFile> {
        val results = mutableListOf<WebshareFile>()
        try {
            val factory = XmlPullParserFactory.newInstance()
            val parser = factory.newPullParser()
            parser.setInput(StringReader(xml))

            var eventType = parser.eventType
            var insideFile = false
            var ident = ""
            var name = ""
            var sizeBytes = 0L
            var posVotes = 0
            var negVotes = 0
            var passwordProtected = false
            var stripeUrl: String? = null

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        when (parser.name) {
                            "file" -> {
                                insideFile = true
                                ident = ""
                                name = ""
                                sizeBytes = 0L
                                posVotes = 0
                                negVotes = 0
                                passwordProtected = false
                                stripeUrl = null
                            }
                            "ident" -> if (insideFile) ident = parser.nextText().trim()
                            "name" -> if (insideFile) name = parser.nextText().trim()
                            "size" -> if (insideFile) sizeBytes = parser.nextText().trim().toLongOrNull() ?: 0L
                            "pos_votes" -> if (insideFile) posVotes = parser.nextText().trim().toIntOrNull() ?: 0
                            "neg_votes" -> if (insideFile) negVotes = parser.nextText().trim().toIntOrNull() ?: 0
                            "password" -> if (insideFile) passwordProtected = parser.nextText().trim() == "1"
                            "stripe" -> if (insideFile) stripeUrl = parser.nextText().trim().takeIf { it.isNotEmpty() }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (parser.name == "file" && insideFile) {
                            insideFile = false
                            if (ident.isNotEmpty() && name.isNotEmpty()) {
                                results.add(
                                    WebshareFile(
                                        ident = ident,
                                        name = name,
                                        sizeBytes = sizeBytes,
                                        posVotes = posVotes,
                                        negVotes = negVotes,
                                        passwordProtected = passwordProtected,
                                        stripeUrl = stripeUrl
                                    )
                                )
                            }
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (_: Exception) {
            // Fallback regex parser if malformed characters appear in XML
            val fileBlocks = Regex("<file>([\\s\\S]*?)</file>").findAll(xml)
            for (block in fileBlocks) {
                val content = block.groupValues[1]
                val ident = extractTagValue(content, "ident") ?: continue
                val name = extractTagValue(content, "name") ?: continue
                val size = extractTagValue(content, "size")?.toLongOrNull() ?: 0L
                val pos = extractTagValue(content, "pos_votes")?.toIntOrNull() ?: 0
                val neg = extractTagValue(content, "neg_votes")?.toIntOrNull() ?: 0
                val pwd = extractTagValue(content, "password") == "1"
                val stripe = extractTagValue(content, "stripe")
                results.add(
                    WebshareFile(
                        ident = ident,
                        name = name,
                        sizeBytes = size,
                        posVotes = pos,
                        negVotes = neg,
                        passwordProtected = pwd,
                        stripeUrl = stripe
                    )
                )
            }
        }
        return results
    }
}
