/**
 * ZZULI 教学评价自动化工具
 *
 * @author ShiYi
 * @since 2026-01-07
 *
 * 采用 iOS 设计美学风格的终端交互界面
 * 自动完成所有待评价课程的满分评价
 */

import jdk.jfr.internal.LogLevel
import mu.KotlinLogging
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.net.CookieHandler
import java.net.CookieManager
import java.net.CookiePolicy
import java.nio.charset.Charset
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import java.security.cert.X509Certificate
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import org.json.JSONObject
import java.net.URI
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.net.ssl.HostnameVerifier

// 创建全局 Logger
private val logger = KotlinLogging.logger("AutoEvaluation")

// ═══════════════════════════════════════════════════════════════
// ANSI Color Constants - iOS 风格配色
// ═══════════════════════════════════════════════════════════════
object Colors {
    const val RESET = "\u001B[0m"
    const val BOLD = "\u001B[1m"

    // iOS System Colors
    const val BLUE = "\u001B[38;5;33m"       // iOS Blue #007AFF
    const val GREEN = "\u001B[38;5;35m"      // iOS Green #34C759
    const val RED = "\u001B[38;5;196m"       // iOS Red #FF3B30
    const val ORANGE = "\u001B[38;5;208m"    // iOS Orange #FF9500
    const val YELLOW = "\u001B[38;5;220m"    // iOS Yellow #FFCC00
    const val PURPLE = "\u001B[38;5;135m"    // iOS Purple #AF52DE
    const val CYAN = "\u001B[38;5;45m"       // iOS Teal #5AC8FA
    const val GRAY = "\u001B[38;5;245m"      // iOS Gray #8E8E93
    const val WHITE = "\u001B[97m"

    // Background Colors
    const val BG_BLUE = "\u001B[48;5;33m"
    const val BG_GREEN = "\u001B[48;5;35m"
}

// ═══════════════════════════════════════════════════════════════
// UI Components - iOS 风格终端组件
// ═══════════════════════════════════════════════════════════════
object UI {
    private const val WIDTH = 60

    fun clear() = print("\u001B[2J\u001B[H")

    fun banner() {
        println()
        println("${Colors.BLUE}${Colors.BOLD}╔${"═".repeat(WIDTH)}╗${Colors.RESET}")
        println("${Colors.BLUE}${Colors.BOLD}║${Colors.RESET}${" ".repeat((WIDTH - 28) / 2)}${Colors.WHITE}${Colors.BOLD}⭐ ZZULI 教学评价助手 ⭐${Colors.RESET}${" ".repeat((WIDTH - 28) / 2 + (WIDTH - 28) % 2)}${Colors.BLUE}${Colors.BOLD}║${Colors.RESET}")
        println("${Colors.BLUE}${Colors.BOLD}║${Colors.RESET}${" ".repeat((WIDTH - 22) / 2)}${Colors.GRAY}Teaching Evaluation Helper${Colors.RESET}${" ".repeat((WIDTH - 26) / 2)}${Colors.BLUE}${Colors.BOLD}║${Colors.RESET}")
        println("${Colors.BLUE}${Colors.BOLD}╚${"═".repeat(WIDTH)}╝${Colors.RESET}")
        println()
    }

    fun divider(char: Char = '─') = println("${Colors.GRAY}${char.toString().repeat(WIDTH + 2)}${Colors.RESET}")

    fun info(message: String) = println("${Colors.CYAN}ℹ${Colors.RESET}  $message")
    fun success(message: String) = println("${Colors.GREEN}✓${Colors.RESET}  $message")
    fun warning(message: String) = println("${Colors.ORANGE}⚠${Colors.RESET}  $message")
    fun error(message: String) = println("${Colors.RED}✗${Colors.RESET}  $message")

    fun progress(current: Int, total: Int, name: String) {
        val percent = (current.toDouble() / total * 100).toInt()
        val filled = (percent / 5)
        val empty = 20 - filled
        val bar = "${Colors.GREEN}${"█".repeat(filled)}${Colors.GRAY}${"░".repeat(empty)}${Colors.RESET}"
        print("\r${Colors.CYAN}⏳${Colors.RESET}  [$bar] $percent% - $name${" ".repeat(20)}")
    }

    fun progressDone() = println()

    fun prompt(message: String): String {
        print("${Colors.BLUE}▶${Colors.RESET}  $message: ")
        return readLine() ?: ""
    }

    fun promptPassword(message: String): String {
        print("${Colors.BLUE}▶${Colors.RESET}  $message: ")
        // 在终端中尝试隐藏密码输入
        return try {
            System.console()?.readPassword()?.concatToString() ?: readLine() ?: ""
        } catch (e: Exception) {
            readLine() ?: ""
        }
    }

    fun section(title: String) {
        println()
        println("${Colors.WHITE}${Colors.BOLD}【 $title 】${Colors.RESET}")
        divider()
    }

    fun courseItem(index: Int, course: Course, status: String = "") {
        val statusIcon = when {
            status == "done" -> "${Colors.GREEN}✓${Colors.RESET}"
            status == "skip" -> "${Colors.GRAY}○${Colors.RESET}"
            status == "fail" -> "${Colors.RED}✗${Colors.RESET}"
            else -> "${Colors.ORANGE}◉${Colors.RESET}"
        }
        println("   $statusIcon ${Colors.WHITE}${index.toString().padStart(2)}. ${Colors.RESET}${course.courseName.take(25).padEnd(25)} ${Colors.GRAY}| ${course.teacherName}${Colors.RESET}")
    }

    fun menu(options: List<String>): Int {
        println()
        options.forEachIndexed { index, option ->
            println("   ${Colors.BLUE}[${index + 1}]${Colors.RESET} $option")
        }
        println()
        val choice = prompt("请选择操作")
        return choice.toIntOrNull() ?: 0
    }

    fun confirm(message: String): Boolean {
        val answer = prompt("$message (y/n)")
        return answer.lowercase() in listOf("y", "yes", "是", "确认")
    }

    fun spinner(message: String, action: () -> Unit) {
        val frames = listOf("⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏")
        var running = true

        val spinnerThread = Thread {
            var i = 0
            while (running) {
                print("\r${Colors.CYAN}${frames[i % frames.size]}${Colors.RESET}  $message")
                Thread.sleep(80)
                i++
            }
        }
        spinnerThread.start()

        try {
            action()
        } finally {
            running = false
            spinnerThread.join()
            print("\r${" ".repeat(message.length + 10)}\r")
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// Data Models
// ═══════════════════════════════════════════════════════════════
data class Course(
    val jsid: String,        // 教师ID
    val teacherId: String,   // 教师工号
    val teacherName: String, // 教师姓名
    val courseCode: String,  // 课程代码
    val courseName: String,  // 课程名称
    val classCode: String,   // 上课班级代码
    val credit: String,      // 学分
    val evaluationType: String = "01",  // 评价类别
    val isMainTeacher: String = "1",    // 是否主讲教师
    val studentCode: String = "",       // 学生代码
    val evaluated: Boolean = false      // 是否已评价
)

data class EvaluationPeriod(
    val year: String,          // 学年
    val semester: String,      // 学期
    val periodCode: String,    // 评价轮次代码
    val startDate: String,     // 开始日期
    val endDate: String,       // 结束日期
    val isIndicator: String,   // 是否指标评价
    val isQuestionnaire: String // 是否问卷评价
)

data class EvaluationIndicator(
    val code: String,      // 指标代码
    val maxScore: String,  // 满分值
    val levelCode: String  // 等级代码
)

// ═══════════════════════════════════════════════════════════════
// HTTP Client - 网络请求工具（基于首页单点登录实现）
// ═══════════════════════════════════════════════════════════════
class HttpClient {
    companion object {
        // 门户地址 (public.js: var baseUrl)
        const val PORTAL_URL = "https://campus.zzuli.edu.cn/portal-pc"
        // CAS 认证地址 (public.js: var requestUrl)
        const val CAS_URL = "https://kys.zzuli.edu.cn/cas"
        // 教务系统地址
        const val JWGL_URL = "https://jwgl.zzuli.edu.cn"
        // 门户登录回调地址 (public.js: var serviceUrl)
        const val SERVICE_URL = "$PORTAL_URL/login/pcLogin"
    }

    private val cookieManager = CookieManager().apply {
        setCookiePolicy(CookiePolicy.ACCEPT_ALL)
    }

    private lateinit var sslContext: SSLContext

    init {
        CookieHandler.setDefault(cookieManager)
        trustAllCertificates()
    }

    private fun trustAllCertificates() {
        val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
            override fun getAcceptedIssuers(): Array<X509Certificate>? = null
            override fun checkClientTrusted(certs: Array<X509Certificate>, authType: String) {}
            override fun checkServerTrusted(certs: Array<X509Certificate>, authType: String) {}
        })

        sslContext = SSLContext.getInstance("SSL")
        sslContext.init(null, trustAllCerts, java.security.SecureRandom())
        HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.socketFactory)
        HttpsURLConnection.setDefaultHostnameVerifier { _, _ -> true }
    }

    private fun createConnection(urlStr: String): HttpURLConnection {
        val url = URL(urlStr)
        val connection = url.openConnection() as HttpURLConnection

        // 如果是HTTPS连接，设置SSL配置
        if (connection is HttpsURLConnection) {
            connection.sslSocketFactory = sslContext.socketFactory
            connection.hostnameVerifier = HostnameVerifier { _, _ -> true }
        }

        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
        connection.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
        connection.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
        connection.connectTimeout = 30000
        connection.readTimeout = 30000
        connection.instanceFollowRedirects = false  // 手动处理重定向以获取所有cookies
        return connection
    }

    /**
     * Base64 编码密码（与首页 index.js 的 doLogin 函数一致）
     * 格式: {gilight}_<base64(password)>
     */
    private fun encodePassword(password: String): String {
        val encoded = java.util.Base64.getEncoder().encodeToString(password.toByteArray(Charsets.UTF_8))
        return "{gilight}_$encoded"
    }

    /**
     * 发送 GET 请求并手动处理重定向
     */
    fun httpGet(url: String, followRedirects: Boolean = true): String {
        var currentUrl = url
        var maxRedirects = 10

        while (maxRedirects > 0) {
            val connection = createConnection(currentUrl)
            connection.requestMethod = "GET"

            val responseCode = connection.responseCode

            // 读取响应内容
            val content = try {
                connection.inputStream.bufferedReader().use { it.readText() }
            } catch (e: Exception) {
                connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            }

            // 检查是否需要重定向
            if (followRedirects && responseCode in 300..399) {
                val location = connection.getHeaderField("Location")
                if (location != null) {
                    logger.debug { "重定向: $currentUrl -> $location" }
                    currentUrl = when {
                        location.startsWith("http://") || location.startsWith("https://") -> location
                        location.startsWith("/") -> {
                            // 绝对路径，拼接到当前域名
                            val baseUrl = URL(currentUrl)
                            "${baseUrl.protocol}://${baseUrl.host}$location"
                        }
                        else -> {
                            // 相对路径，拼接到当前URL
                            val baseUrl = URL(currentUrl)
                            val path = baseUrl.path.substringBeforeLast('/') + "/$location"
                            "${baseUrl.protocol}://${baseUrl.host}$path"
                        }
                    }
                    logger.debug { "新URL: $currentUrl" }
                    maxRedirects--
                    continue
                }
            }

            return content
        }
        return ""
    }

    /**
     * 发送 POST 请求
     */
    fun httpPost(url: String, params: Map<String, String>, followRedirects: Boolean = false, useGBK: Boolean = false): Pair<Int, String> {
        val connection = createConnection(url)
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
        connection.setRequestProperty("X-Requested-With", "XMLHttpRequest")

        val postData = params.entries.joinToString("&") {
            "${URLEncoder.encode(it.key, "UTF-8")}=${URLEncoder.encode(it.value, "UTF-8")}"
        }
        OutputStreamWriter(connection.outputStream, "UTF-8").use { it.write(postData) }

        val responseCode = connection.responseCode

        // 根据参数选择编码
        val charset = if (useGBK) Charset.forName("GBK") else Charsets.UTF_8
        val content = try {
            connection.inputStream.bufferedReader(charset).use { it.readText() }
        } catch (e: Exception) {
            connection.errorStream?.bufferedReader(charset)?.use { it.readText() } ?: ""
        }

        // 如果需要跟随重定向
        if (followRedirects && responseCode in 300..399) {
            val location = connection.getHeaderField("Location")
            if (location != null) {
                return Pair(responseCode, httpGet(location, true))
            }
        }

        return Pair(responseCode, content)
    }

    /**
     * 提交表单到 CAS 并跟随所有重定向（模拟浏览器表单提交）
     */
    fun submitCasForm(casLoginUrl: String, params: Map<String, String>): Boolean {
        var currentUrl = casLoginUrl
        var isPost = true
        var postParams = params
        var maxRedirects = 15

        while (maxRedirects > 0) {
            val connection = createConnection(currentUrl)

            if (isPost) {
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")

                val postData = postParams.entries.joinToString("&") {
                    "${URLEncoder.encode(it.key, "UTF-8")}=${URLEncoder.encode(it.value, "UTF-8")}"
                }
                OutputStreamWriter(connection.outputStream, "UTF-8").use { it.write(postData) }
            } else {
                connection.requestMethod = "GET"
            }

            val responseCode = connection.responseCode

            // 读取响应（获取cookies）
            try {
                connection.inputStream.bufferedReader().use { it.readText() }
            } catch (e: Exception) {
                try { connection.errorStream?.bufferedReader()?.use { it.readText() } } catch (e2: Exception) {}
            }

            // 检查重定向
            if (responseCode in 300..399) {
                val location = connection.getHeaderField("Location")
                if (location != null) {
                    currentUrl = when {
                        location.startsWith("http://") || location.startsWith("https://") -> location
                        location.startsWith("/") -> {
                            // 绝对路径
                            val base = URL(currentUrl)
                            "${base.protocol}://${base.host}$location"
                        }
                        else -> {
                            // 相对路径
                            val base = URL(currentUrl)
                            val path = base.path.substringBeforeLast('/') + "/$location"
                            "${base.protocol}://${base.host}$path"
                        }
                    }
                    isPost = false  // 重定向后使用 GET
                    maxRedirects--
                    continue
                }
            }

            // 成功或其他状态码
            return responseCode == 200
        }
        return false
    }

    /**
     * 获取 CAS 登录参数 (lt, execution)
     * 对应首页 index.js 中的 JSONP 请求:
     * $.ajax({ url: requestUrl + "/login?action=getlt&service=" + serviceUrl, dataType: "jsonp", ... })
     */
    fun getCasLoginParams(serviceUrl: String): Pair<String, String>? {
        return try {
            // JSONP 请求获取 lt 和 execution
            val url = "$CAS_URL/login?action=getlt&service=${URLEncoder.encode(serviceUrl, "UTF-8")}&callback=jsonpCallback"
            val response = httpGet(url, false)

            // 解析 JSONP 响应: jsonpCallback({"lt":"...", "execution":"..."})
            val ltPattern = """"lt"\s*:\s*"([^"]+)"""".toRegex()
            val executionPattern = """"execution"\s*:\s*"([^"]+)"""".toRegex()

            val lt = ltPattern.find(response)?.groupValues?.get(1)
            val execution = executionPattern.find(response)?.groupValues?.get(1)

            if (lt != null && execution != null) {
                Pair(lt, execution)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 执行首页单点登录
     * 完全按照首页 index.js 的 doLogin 函数流程实现
     */
    fun login(username: String, password: String): Boolean {
        return try {
            val encodedPassword = encodePassword(password)

            // ══════════════════════════════════════════════════════════════
            // Step 1: 调用门户认证接口验证用户名密码
            // index.js: $.ajax({ url: baseUrl + '/login/authentication', data: {username, password}, ... })
            // ══════════════════════════════════════════════════════════════
            UI.info("正在验证账号...")
            val (_, authResponse) = httpPost("$PORTAL_URL/login/authentication", mapOf(
                "username" to username,
                "password" to encodedPassword
            ))

            // 检查认证结果 - 成功时 res.obj.obj.code == 0
            // 支持多种响应格式: "code":0 或 "code":"0" 或 "success":true
            val isSuccess = authResponse.contains("\"success\":true") ||
                           authResponse.contains("\"code\":\"0\"") ||
                           authResponse.contains("\"code\":0")

            if (!isSuccess) {
                val msgPattern = """"msg"\s*:\s*"([^"]+)"""".toRegex()
                val errorMsg = msgPattern.find(authResponse)?.groupValues?.get(1) ?: "用户名或密码错误"
                UI.error("认证失败: $errorMsg")
                return false
            }
            UI.success("账号验证通过")

            // ══════════════════════════════════════════════════════════════
            // Step 2: 获取 CAS 登录参数 (lt, execution)
            // index.js: $.ajax({ url: requestUrl + "/login?action=getlt&service=" + serviceUrl, dataType: "jsonp", ... })
            // ══════════════════════════════════════════════════════════════
            UI.info("正在获取登录凭证...")
            val casParams = getCasLoginParams(SERVICE_URL)
            if (casParams == null) {
                UI.error("获取 CAS 登录参数失败")
                return false
            }
            val (lt, execution) = casParams
            UI.success("获取凭证成功")

            // ══════════════════════════════════════════════════════════════
            // Step 3: 提交 CAS 登录表单（模拟 $('#loginForm').submit()）
            // 表单提交到: requestUrl + '/login?service=' + serviceUrl
            // 表单字段: username, password, lt, execution, _eventId=submit
            // ══════════════════════════════════════════════════════════════
            UI.info("正在登录门户系统...")
            val portalCasLoginUrl = "$CAS_URL/login?service=${URLEncoder.encode(SERVICE_URL, "UTF-8")}"
            val formParams = mapOf(
                "username" to username,
                "password" to encodedPassword,
                "lt" to lt,
                "execution" to execution,
                "_eventId" to "submit"
            )

            val casSuccess = submitCasForm(portalCasLoginUrl, formParams)
            if (!casSuccess) {
                UI.warning("CAS 登录可能未完全成功，继续尝试...")
            } else {
                UI.success("门户登录成功")
            }

            // ══════════════════════════════════════════════════════════════
            // Step 4: 访问教务系统 - 使用 /caslogin 端点完成 SSO
            // 浏览器中登录后访问 https://jwgl.zzuli.edu.cn/caslogin 可直接进入
            // ══════════════════════════════════════════════════════════════
            UI.info("正在连接教务系统...")

            // 访问 /caslogin 端点，会自动通过 CAS SSO 完成认证
            val jwglCasLoginUrl = "$JWGL_URL/caslogin"
            val jwglContent = httpGet(jwglCasLoginUrl, true)

            // 验证是否成功进入教务系统
            if (jwglContent.contains("凭证已失效") || jwglContent.contains("重新登录")) {
                UI.error("教务系统CAS单点登录失败")
                return false
            }

            UI.success("教务系统登录成功")
            true

        } catch (e: Exception) {
            UI.error("登录过程出错: ${e.message}")
            false
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 教务系统专用方法
    // ═══════════════════════════════════════════════════════════════

    fun jwglGet(path: String, params: Map<String, String> = emptyMap()): String {
        val queryString = if (params.isEmpty()) "" else "?" + params.entries.joinToString("&") {
            "${URLEncoder.encode(it.key, "UTF-8")}=${URLEncoder.encode(it.value, "UTF-8")}"
        }
        val fullUrl = "$JWGL_URL$path$queryString"
        logger.debug { "调用教务系统GET: $fullUrl" }

        // 打印当前cookies
        val cookies = cookieManager.cookieStore.cookies
        logger.debug { "当前Cookies数量: ${cookies.size}" }
        if (cookies.isEmpty()) {
            logger.warn { "没有可用的Cookies！" }
        }
        cookies.take(3).forEach { cookie ->
            logger.debug { "Cookie: ${cookie.name}=${cookie.value.take(20)}... domain=${cookie.domain} path=${cookie.path}" }
        }

        return httpGet(fullUrl, true)
    }

    fun jwglPost(path: String, params: Map<String, String>): String {
        val fullUrl = "$JWGL_URL$path"
        logger.debug { "调用教务系统POST: $fullUrl" }
        logger.debug { "请求参数: $params" }

        // 打印当前cookies
        val cookies = cookieManager.cookieStore.cookies
        logger.debug { "当前Cookies数量: ${cookies.size}" }
        if (cookies.isEmpty()) {
            logger.warn { "没有可用的Cookies！请检查登录流程" }
        }
        cookies.take(5).forEach { cookie ->
            logger.debug { "Cookie: ${cookie.name}=${cookie.value.take(30)}... domain=${cookie.domain} path=${cookie.path}" }
        }

        // 创建连接并添加额外的请求头
        val connection = createConnection(fullUrl)
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
        connection.setRequestProperty("X-Requested-With", "XMLHttpRequest")
        connection.setRequestProperty("Referer", "$JWGL_URL/")  // 添加Referer
        connection.setRequestProperty("Origin", JWGL_URL)  // 添加Origin

        // 发送POST数据
        val postData = params.entries.joinToString("&") {
            "${URLEncoder.encode(it.key, "UTF-8")}=${URLEncoder.encode(it.value, "UTF-8")}"
        }
        OutputStreamWriter(connection.outputStream, "UTF-8").use { it.write(postData) }

        // 获取响应
        val responseCode = connection.responseCode
        logger.debug { "POST响应状态码: $responseCode" }

        val result = try {
            val inputStream = connection.inputStream
            val bytes = inputStream.readBytes()
            inputStream.close()

            // 检测Content-Type头中的编码
            val contentType = connection.contentType ?: ""
            val charset = when {
                contentType.contains("charset=GBK", ignoreCase = true) -> Charset.forName("GBK")
                contentType.contains("charset=GB2312", ignoreCase = true) -> Charset.forName("GBK")
                else -> {
                    // 尝试UTF-8解码，如果有乱码则使用GBK
                    val utf8Test = String(bytes, Charsets.UTF_8)
                    if (utf8Test.contains("charset=GBK", ignoreCase = true) ||
                        utf8Test.contains("charset=GB", ignoreCase = true) ||
                        utf8Test.contains("�")) {
                        Charset.forName("GBK")
                    } else {
                        Charsets.UTF_8
                    }
                }
            }

            val result = String(bytes, charset)
            logger.debug { "POST响应长度: ${result.length} 字符 (编码: ${charset.name()})" }
            result
        } catch (e: Exception) {
            val errorContent = try {
                connection.errorStream?.readBytes()?.let { String(it, charset("GBK")) } ?: ""
            } catch (e2: Exception) {
                ""
            }
            logger.error(e) { "POST请求失败: ${e.message}" }
            logger.error { "错误响应: ${errorContent.take(200)}" }
            errorContent
        }

        if (result.isEmpty()) {
            logger.warn { "POST返回空响应！" }
        }
        return result
    }

    // ═══════════════════════════════════════════════════════════════
    // 二维码登录相关方法
    // ═══════════════════════════════════════════════════════════════

    /**
     * 获取二维码登录的 UUID
     * 对应 index.js: $.ajax({ url: baseUrl + "/qrlogin/getUserUUID", ... })
     */
    fun getQrLoginUUID(): String? {
        return try {
            val (_, response) = httpPost("$PORTAL_URL/qrlogin/getUserUUID", emptyMap())
            // 解析响应 {"success":true,"obj":"uuid-string"}
            val uuidPattern = """"obj"\s*:\s*"([^"]+)"""".toRegex()
            uuidPattern.find(response)?.groupValues?.get(1)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 使用 WebSocket 监听扫码状态（完全对应首页实现）
     * WebSocket URL: wss://campus.zzuli.edu.cn/portal-pc/websocket/{uuid}
     *
     * 返回: Pair<状态, 数据Map>
     * 状态: "waiting" | "scanning" | "confirmed" | "expired" | "error"
     */
    fun listenQrLoginWebSocket(uuid: String, timeout: Long = 120_000L): Pair<String, Map<String, String>> {
        val wsUrl = "wss://campus.zzuli.edu.cn/portal-pc/websocket/$uuid"
        val latch = CountDownLatch(1)
        var result: Pair<String, Map<String, String>> = Pair("waiting", emptyMap())

        try {
            // 创建 WebSocket 客户端（信任所有证书）
            val client = object : WebSocketClient(URI(wsUrl)) {
                override fun onOpen(handshakedata: ServerHandshake?) {
                    println("${Colors.GREEN}✓${Colors.RESET}  WebSocket 连接成功")
                    // 发送初始数据（对应 ws.send("发送数据")）
                    send("发送数据")
                }

                override fun onMessage(message: String?) {
                    if (message == null) return

                    try {
                        logger.debug { "[WebSocket] 收到消息: $message" }

                        val json = JSONObject(message)
                        val type = json.optInt("type", -1)

                        when (type) {
                            1 -> {
                                // 扫描成功，等待确认
                                result = Pair("scanning", emptyMap())
                                UI.success("扫描成功！请在手机上确认登录...")
                            }
                            2 -> {
                                // 确认登录，获取用户名密码
                                val content = json.optJSONObject("content")
                                if (content != null) {
                                    val username = content.optString("username", "")
                                    val password = content.optString("password", "")

                                    logger.debug { "原始密码格式: $password" }

                                    if (username.isNotEmpty() && password.isNotEmpty()) {
                                        result = Pair("confirmed", mapOf(
                                            "username" to username,
                                            "password" to password
                                        ))
                                        UI.success("授权成功！")
                                        latch.countDown()  // 成功，停止等待
                                    }
                                }
                            }
                            else -> {
                                logger.debug { "[WebSocket] 未知消息类型: $type" }
                            }
                        }
                    } catch (e: Exception) {
                        logger.error(e) { "解析 WebSocket 消息失败" }
                    }
                }

                override fun onClose(code: Int, reason: String?, remote: Boolean) {
                    logger.debug { "[WebSocket] 连接关闭: $reason" }
                    latch.countDown()
                }

                override fun onError(ex: Exception?) {
                    logger.error(ex) { "WebSocket 错误" }
                    result = Pair("error", emptyMap())
                    latch.countDown()
                }
            }

            // 连接 WebSocket
            client.connect()

            // 等待结果或超时
            val success = latch.await(timeout, TimeUnit.MILLISECONDS)

            client.close()

            if (!success) {
                result = Pair("expired", emptyMap())
            }

        } catch (e: Exception) {
            logger.error(e) { "WebSocket 连接失败" }
            result = Pair("error", emptyMap())
        }

        return result
    }

    @Deprecated("Use listenQrLoginWebSocket instead")
    fun checkQrLoginStatus(uuid: String): Pair<String, Map<String, String>> {
        // 保留旧方法作为备用
        return try {
            val (statusCode, response) = httpPost("$PORTAL_URL/qrlogin/checkQrLogin", mapOf("uuid" to uuid))

            when {
                response.contains("\"type\":2") || (response.contains("username") && response.contains("password")) -> {
                    val usernamePattern = """"username"\s*:\s*"([^"]+)"""".toRegex()
                    val passwordPattern = """"password"\s*:\s*"([^"]+)"""".toRegex()

                    val username = usernamePattern.find(response)?.groupValues?.get(1) ?: ""
                    val password = passwordPattern.find(response)?.groupValues?.get(1) ?: ""

                    if (username.isNotEmpty() && password.isNotEmpty()) {
                        Pair("confirmed", mapOf("username" to username, "password" to password))
                    } else {
                        Pair("waiting", emptyMap())
                    }
                }
                else -> Pair("waiting", emptyMap())
            }
        } catch (e: Exception) {
            Pair("error", emptyMap())
        }
    }

    /**
     * 使用已编码的密码登录（二维码扫码后获取的密码已经是编码后的）
     * 支持两种密码格式：
     * 1. {gilight}_base64 - 账号密码登录
     * 2. {scan}{JSON} - 扫码登录
     */
    fun loginWithEncodedPassword(username: String, encodedPassword: String): Boolean {
        return try {
            logger.debug { "登录用户: $username" }
            logger.debug { "密码格式: ${encodedPassword.take(50)}..." }

            // ══════════════════════════════════════════════════════════════
            // Step 1: 调用门户认证接口验证
            // 扫码登录的密码格式是 {scan}{...}，需要直接传递
            // ══════════════════════════════════════════════════════════════
            UI.info("正在验证账号...")
            val (statusCode, authResponse) = httpPost("$PORTAL_URL/login/authentication", mapOf(
                "username" to username,
                "password" to encodedPassword  // 直接使用原始密码，不再编码
            ))

            logger.debug { "认证响应状态码: $statusCode" }
            logger.debug { "认证响应内容: ${authResponse.take(200)}" }

            // 检查认证是否成功 - 支持多种格式
            // {"success":true,...,"obj":{"obj":{"code":"0"}}} 或 {"code":0}
            val isSuccess = authResponse.contains("\"success\":true") ||
                           authResponse.contains("\"code\":\"0\"") ||
                           authResponse.contains("\"code\":0")

            if (!isSuccess) {
                val msgPattern = """"msg"\s*:\s*"([^"]+)"""".toRegex()
                val errorMsg = msgPattern.find(authResponse)?.groupValues?.get(1) ?: "认证失败"
                UI.error("认证失败: $errorMsg")
                return false
            }
            UI.success("账号验证通过")

            // ══════════════════════════════════════════════════════════════
            // Step 2: 获取 CAS 登录参数
            // ══════════════════════════════════════════════════════════════
            UI.info("正在获取登录凭证...")
            val casParams = getCasLoginParams(SERVICE_URL)
            if (casParams == null) {
                UI.error("获取 CAS 登录参数失败")
                return false
            }
            val (lt, execution) = casParams
            UI.success("获取凭证成功")

            // ══════════════════════════════════════════════════════════════
            // Step 3: 提交 CAS 登录表单
            // ══════════════════════════════════════════════════════════════
            UI.info("正在登录门户系统...")
            val portalCasUrl = "$CAS_URL/login?service=${URLEncoder.encode(SERVICE_URL, "UTF-8")}"
            submitCasForm(portalCasUrl, mapOf(
                "username" to username,
                "password" to encodedPassword,  // 直接使用原始密码
                "lt" to lt,
                "execution" to execution,
                "_eventId" to "submit"
            ))
            UI.success("门户登录成功")

            // ══════════════════════════════════════════════════════════════
            // Step 4: 访问教务系统 - 使用 /caslogin 端点完成 SSO
            // 浏览器中登录后访问 https://jwgl.zzuli.edu.cn/caslogin 可直接进入
            // ══════════════════════════════════════════════════════════════
            UI.info("正在连接教务系统...")

            // 访问 /caslogin 端点，会自动通过 CAS SSO 完成认证
            val jwglCasUrl = "$JWGL_URL/caslogin"
            logger.debug { "访问教务系统CAS登录端点: $jwglCasUrl" }

            val jwglContent = httpGet(jwglCasUrl, true)  // 跟随重定向

            logger.debug { "教务系统响应长度: ${jwglContent.length}" }
            logger.debug { "教务系统响应前200字符: ${jwglContent.take(200)}" }

            // 检查是否登录成功
            if (jwglContent.contains("凭证已失效") || jwglContent.contains("重新登录")) {
                UI.error("教务系统CAS单点登录失败")
                return false
            }

            UI.success("教务系统登录成功")
            true
        } catch (e: Exception) {
            logger.error(e) { "登录过程出错" }
            UI.error("登录过程出错: ${e.message}")
            false
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// Evaluation Service - 评价服务
// ═══════════════════════════════════════════════════════════════
class EvaluationService(private val httpClient: HttpClient) {

    // 默认评语
    private val defaultComment = "老师教学认真负责，授课内容充实，课堂氛围活跃，注重培养学生的实践能力和创新思维。"

    /**
     * 获取当前评价轮次信息
     */
    fun getEvaluationPeriod(): EvaluationPeriod? {
        return try {
            // 先访问教务系统主页以确保session激活
            logger.debug { "预热session：访问教务系统主页..." }
            try {
                val homeResponse = httpClient.httpGet("${HttpClient.JWGL_URL}/", true)
                logger.debug { "主页响应长度: ${homeResponse.length}" }

                // 检查是否需要重新登录
                if (homeResponse.contains("凭证已失效") || homeResponse.contains("重新登录")) {
                    logger.error { "Session已失效，需要重新登录" }
                    return null
                }
            } catch (e: Exception) {
                logger.warn(e) { "访问主页失败" }
            }

            val response = httpClient.jwglPost("/jw/wspjZbpjWjdc/getPjlcInfo.action", mapOf("pjzt_m" to "20"))

            logger.debug { "评价轮次API完整响应:\n$response" }
            logger.debug { "响应长度: ${response.length} 字符" }

            // 响应格式是HTML option标签: <option value='{"xn":"2025",...}'>2025-2026学年第一学期期末学生评教</option>
            // 提取value属性中的JSON
            val optionPattern = """<option[^>]*value='([^']+)'[^>]*>""".toRegex()
            val matchResult = optionPattern.find(response)

            if (matchResult == null) {
                logger.error { "无法找到评价轮次option标签" }
                logger.info { "响应格式可能不是预期的HTML option标签" }
                return null
            }

            val jsonValue = matchResult.groupValues[1]
            logger.debug { "提取的JSON: $jsonValue" }

            // 解析 JSON
            val year = extractJsonValue(jsonValue, "xn")
            val semester = extractJsonValue(jsonValue, "xq_m")
            val periodCode = extractJsonValue(jsonValue, "lcdm")

            logger.debug { "解析结果: year=$year, semester=$semester, periodCode=$periodCode" }

            if (year == null || semester == null || periodCode == null) {
                logger.error { "无法解析评价轮次信息" }
                return null
            }

            val startDate = extractJsonValue(jsonValue, "qsrq") ?: ""
            val endDate = extractJsonValue(jsonValue, "jsrq") ?: ""
            val isIndicator = extractJsonValue(jsonValue, "sfzbpj") ?: "1"
            val isQuestionnaire = extractJsonValue(jsonValue, "sfwjpj") ?: "1"

            EvaluationPeriod(year, semester, periodCode, startDate, endDate, isIndicator, isQuestionnaire)
        } catch (e: Exception) {
            logger.error(e) { "获取评价轮次异常" }
            null
        }
    }

    /**
     * 获取待评价课程列表
     */
    fun getPendingCourses(period: EvaluationPeriod): List<Course> {
        return try {
            // 使用POST请求获取课程列表，需要提交表单参数
            val response = httpClient.jwglPost("/taglib/DataTable.jsp?tableId=50058&fre=1", mapOf(
                "xn" to period.year,
                "xq" to period.semester,
                "pjlc" to period.periodCode,
                "pjzt_m" to "20",
                "sfzbpj" to period.isIndicator,
                "sfwjpj" to period.isQuestionnaire,
                "pjfsbz" to "0",
                "qyxjkc" to "",
                "zysx" to "",
                "djs" to "",
                "sfbd" to "0",
                "kgz" to "0",
                "records" to "",
                "menucode" to "S902"
            ))

            logger.debug { "课程列表API响应长度: ${response.length} 字符" }

            // 只在debug模式下打印响应片段
            if (response.length < 2000) {
                logger.debug { "响应内容: $response" }
            } else {
                logger.debug { "响应前500字符: ${response.take(500)}" }
                logger.debug { "响应后500字符: ${response.takeLast(500)}" }
            }

            val courses = parseCourseList(response)
            logger.debug { "解析到 ${courses.size} 门课程" }
            courses
        } catch (e: Exception) {
            logger.error(e) { "获取课程列表异常" }
            emptyList()
        }
    }

    /**
     * 提交单个课程评价
     */
    fun submitEvaluation(period: EvaluationPeriod, course: Course): Boolean {
        return try {
            // 第一步：访问评价页面，获取模板ID和指标/问卷信息
            logger.debug { "正在访问评价页面..." }

            val evaluationPageParams = mapOf(
                "xn" to period.year,
                "xq" to period.semester,
                "pjlc" to period.periodCode,
                "jsid" to course.jsid,
                "kcdm" to course.courseCode,
                "skbjdm" to course.classCode,
                "pjlb_m" to course.evaluationType,
                "sfzjjs" to course.isMainTeacher,
                "yhdm" to course.studentCode,
                "pjzt_m" to "20"
            )

            val evaluationPageUrl = "/student/wspj_tjzbpj_wjdcb_pj.jsp?" +
                evaluationPageParams.entries.joinToString("&") { "${it.key}=${it.value}" }

            val pageHtml = httpClient.jwglGet(evaluationPageUrl)

            // 从页面中提取各种必要参数
            val zbmbPattern = """name\s*=\s*['"]zbmb_m['"]\s+value\s*=\s*['"]([^'"]*)['"]+""".toRegex()
            val wjmbPattern = """name\s*=\s*['"]wjmb_m['"]\s+value\s*=\s*['"]([^'"]*)['"]+""".toRegex()
            val userCodePattern = """name\s*=\s*['"]userCode['"]\s+value\s*=\s*['"]([^'"]*)['"]+""".toRegex()

            val zbmb = zbmbPattern.find(pageHtml)?.groupValues?.get(1) ?: "001"
            val wjmb = wjmbPattern.find(pageHtml)?.groupValues?.get(1) ?: "001"
            val userCode = userCodePattern.find(pageHtml)?.groupValues?.get(1) ?: ""

            // 提取所有指标ID (zbdm)
            // 查找 name="zbdm" value="xxxx" 模式
            val zbdmPattern = """<input[^>]+name\s*=\s*["']zbdm["'][^>]+value\s*=\s*["']([^"']+)["']""".toRegex()
            val indicators = zbdmPattern.findAll(pageHtml).map { it.groupValues[1] }.toList()

            // 提取所有问卷ID (wjdm)
            val wjdmPattern = """<(?:input|textarea)[^>]+name\s*=\s*["']wjdm["'][^>]+value\s*=\s*["']([^"']+)["']""".toRegex()
            val questionnaires = wjdmPattern.findAll(pageHtml).map { it.groupValues[1] }.toList()

            logger.debug { "提取参数 - zbmb: $zbmb, wjmb: $wjmb, userCode: $userCode" }
            logger.debug { "指标数量: ${indicators.size}, 问卷数量: ${questionnaires.size}" }
            if (indicators.isNotEmpty()) {
                logger.debug { "指标ID: ${indicators.joinToString(", ")}" }
            }
            if (questionnaires.isNotEmpty()) {
                logger.debug { "问卷ID: ${questionnaires.joinToString(", ")}" }
            }

            // 第二步：构建并提交评价数据
            val indicatorData = buildIndicatorData(indicators)
            val questionnaireData = buildQuestionnaireData(questionnaires)

            // 构建完整的表单参数（按照浏览器提交的顺序）
            val params = mutableMapOf(
                "wspjZbpjWjdcForm.pjlb_m" to course.evaluationType,
                "wspjZbpjWjdcForm.sfzjjs" to course.isMainTeacher,
                "wspjZbpjWjdcForm.commitZB" to indicatorData,
                "wspjZbpjWjdcForm.commitWJText" to questionnaireData,
                "wspjZbpjWjdcForm.commitWJSelect" to "",
                "wspjZbpjWjdcForm.xn" to period.year,
                "wspjZbpjWjdcForm.xq" to period.semester,
                "wspjZbpjWjdcForm.jsid" to course.jsid,
                "wspjZbpjWjdcForm.kcdm" to course.courseCode,
                "wspjZbpjWjdcForm.skbjdm" to course.classCode,
                "wspjZbpjWjdcForm.pjlc" to period.periodCode,
                "wspjZbpjWjdcForm.userCode" to userCode,
                "wspjZbpjWjdcForm.pjzt_m" to "20",
                "wspjZbpjWjdcForm.zbmb_m" to zbmb,
                "wspjZbpjWjdcForm.wjmb_m" to wjmb,
                "bfzfs_xx" to "",
                "bfzfs_sx" to "",
                "totalcj" to "100",
                "zbSize" to indicators.size.toString(),
                "zbmb" to zbmb,
                "wjmb" to wjmb,
                "wjSize" to questionnaires.size.toString(),
                "menucode_current" to "S902"
            )

            // 添加每个指标的分数字段 sel_scorecj*
            indicators.forEachIndexed { index, _ ->
                params["sel_scorecj$index"] = "10"
            }

            // 添加每个问卷的文本字段 area*
            questionnaires.forEachIndexed { index, _ ->
                params["area$index"] = defaultComment
            }

            val response = httpClient.jwglPost("/jw/wspjZbpjWjdc/save.action", params)

//            // 打印响应以便调试
//            println("${Colors.CYAN}[DEBUG] 评价提交响应: $response${Colors.RESET}")

            // 检查响应
            val isSuccess = response.contains("\"status\":\"200\"") ||
                           response.contains("\"success\":true", ignoreCase = true) ||
                           response.contains("成功") ||
                           response.contains("保存成功", ignoreCase = true) ||
                           response.trim().isEmpty() ||
                           response.trim() == "null"

            if (!isSuccess) {
//                println("${Colors.RED}[ERROR] 评价可能失败，响应内容: $response${Colors.RESET}")
            }

            isSuccess
        } catch (e: Exception) {
//            println("${Colors.RED}[ERROR] 评价提交异常: ${e.message}${Colors.RESET}")
            e.printStackTrace()
            false
        }
    }

    /**
     * 构建满分指标数据
     * 格式：分数@指标代码@等级代码;
     * 例如：10@0001@ ;10@0002@ ;...
     */
    private fun buildIndicatorData(indicators: List<String>): String {
        if (indicators.isEmpty()) {
            // 如果没有获取到指标，使用默认的10个
            return (1..10).joinToString(";") { i ->
                val zbdm = String.format("%04d", i)
                "10@$zbdm@ "
            } + ";"
        }

        // 使用实际获取的指标ID
        return indicators.joinToString(";") { zbdm ->
            "10@$zbdm@ "  // 分数@指标代码@等级代码（空格）
        } + ";"
    }

    /**
     * 构建问卷数据
     * 格式：编号@#@URL编码的内容;
     * 例如：0001@#@%E8%80%81%E5%B8%88...;
     */
    private fun buildQuestionnaireData(questionnaires: List<String>): String {
        if (questionnaires.isEmpty()) {
            // 如果没有获取到问卷，使用默认的
            return "0001@#@${URLEncoder.encode(defaultComment, "UTF-8")};"
        }

        // 使用实际获取的问卷ID
        return questionnaires.joinToString(";") { wjdm ->
            "$wjdm@#@${URLEncoder.encode(defaultComment, "UTF-8")}"
        } + ";"
    }

    /**
     * 从 JSON 字符串中提取值 (简单实现)
     */
    private fun extractJsonValue(json: String, key: String): String? {
        val pattern = """"$key"\s*:\s*"([^"]*)"""".toRegex()
        return pattern.find(json)?.groupValues?.get(1)
    }

    /**
     * 解析课程列表 HTML
     */
    private fun parseCourseList(html: String): List<Course> {
        val courses = mutableListOf<Course>()

        logger.debug { "开始解析课程列表，HTML长度: ${html.length}" }

        // 检查是否包含"评价"相关关键字
        val keywords = listOf("jxpj", "评价", "pjlb", "kcmc", "jsid")
        keywords.forEach { keyword ->
            val count = html.split(keyword).size - 1
            if (count > 0) {
                logger.debug { "发现关键字 '$keyword': $count 次" }
            }
        }

        // 打印包含jxpj的行，以便分析实际格式
        logger.debug { "查找包含'jxpj'的HTML片段（仅显示第一个）" }
        html.lines().firstOrNull { it.contains("jxpj", ignoreCase = true) }?.let { line ->
            logger.debug { line.trim().take(200) }
        }

        // 匹配实际格式: parent.jxpj("{\"xn\":\"2025\",...}","0")
        // 注意：JSON字符串中包含 \" 转义字符，所以使用 (?:\\"|[^"])*? 来匹配
        // (?:\\"| 匹配转义的引号，[^"] 匹配非引号字符
        val pattern = """parent\.jxpj\("(\{(?:\\"|[^"])*?\})","(\d+)"\)""".toRegex()
        val matches = pattern.findAll(html)

        val matchList = matches.toList()
        logger.debug { "正则匹配到 ${matchList.size} 个jxpj调用" }

        if (matchList.isEmpty()) {
            logger.warn { "未找到任何jxpj调用" }
        } else {
            logger.debug { "第一个匹配示例: ${matchList[0].value.take(150)}..." }
        }

        for (match in matchList) {
            try {
                // HTML中的JSON字符串被转义了：\" 需要替换为 "
                val rawJsonStr = match.groupValues[1]
                val jsonStr = rawJsonStr
                    .replace("\\\"", "\"")  // 解码HTML转义的引号
                    .replace("&quot;", "\"")
                    .replace("&#39;", "'")
                    .replace("&lt;", "<")
                    .replace("&gt;", ">")
                    .replace("&amp;", "&")

                val evaluatedFlag = match.groupValues[2]  // "0"表示未评价，"1"表示已评价

                logger.debug { "评价标志: $evaluatedFlag, JSON前100字符: ${jsonStr.take(100)}..." }

                val course = Course(
                    jsid = extractJsonValue(jsonStr, "jsid") ?: continue,
                    teacherId = extractJsonValue(jsonStr, "gh") ?: "",
                    teacherName = extractJsonValue(jsonStr, "xm") ?: "未知教师",
                    courseCode = extractJsonValue(jsonStr, "kcdm") ?: continue,
                    courseName = extractJsonValue(jsonStr, "kcmc") ?: "未知课程",
                    classCode = extractJsonValue(jsonStr, "skbjdm") ?: "",
                    credit = extractJsonValue(jsonStr, "xf") ?: "0",
                    evaluationType = extractJsonValue(jsonStr, "pjlb_m") ?: "01",
                    isMainTeacher = extractJsonValue(jsonStr, "sfzjjs") ?: "1",
                    studentCode = extractJsonValue(jsonStr, "yhdm") ?: "",
                    evaluated = evaluatedFlag == "1"  // "1"表示已评价
                )

                logger.debug { "解析得到课程: ${course.courseName} - ${course.teacherName} (已评价: ${course.evaluated})" }

                if (!course.evaluated) {
                    courses.add(course)
                    logger.info { "添加待评价课程: ${course.courseName} - ${course.teacherName}" }
                }
            } catch (e: Exception) {
                logger.error(e) { "解析课程失败" }
                e.printStackTrace()
                continue
            }
        }

        return courses
    }
}

// ═══════════════════════════════════════════════════════════════
// Main Application
// ═══════════════════════════════════════════════════════════════
class AutoEvaluationApp {
    private val httpClient = HttpClient()
    private val evaluationService = EvaluationService(httpClient)

    fun run() {
        UI.clear()
        UI.banner()

        UI.section("🔐 选择登录方式")
        val loginChoice = UI.menu(listOf(
            "📱 扫码登录（推荐）",
            "🔑 账号密码登录",
            "❌ 退出程序"
        ))

        val loginSuccess = when (loginChoice) {
            1 -> qrCodeLogin()
            2 -> passwordLogin()
            3 -> {
                UI.info("感谢使用，再见！")
                return
            }
            else -> {
                UI.warning("无效选择")
                return
            }
        }

        if (!loginSuccess) {
            UI.error("登录失败")
            return
        }

        println()
        UI.success("登录成功！")

        // 获取评价轮次
        println()
        var period: EvaluationPeriod? = null
        UI.spinner("正在获取评价信息...") {
            period = evaluationService.getEvaluationPeriod()
        }

        if (period == null) {
            UI.warning("当前没有进行中的评价轮次")
            return
        }

        val currentPeriod = period!!
        UI.info("评价轮次: ${currentPeriod.year}学年第${if (currentPeriod.semester == "0") "一" else "二"}学期")
        UI.info("评价时间: ${currentPeriod.startDate} ~ ${currentPeriod.endDate}")

        // 获取待评价课程
        UI.section("📚 待评价课程")
        var courses: List<Course> = emptyList()
        UI.spinner("正在获取课程列表...") {
            courses = evaluationService.getPendingCourses(currentPeriod)
        }

        if (courses.isEmpty()) {
            UI.success("恭喜！所有课程已评价完成 🎉")
            return
        }

        UI.info("共发现 ${courses.size} 门待评价课程：")
        println()
        courses.forEachIndexed { index, course ->
            UI.courseItem(index + 1, course)
        }

        // 显示菜单
        UI.section("🎯 选择操作")
        val choice = UI.menu(listOf(
            "⭐ 一键满分评价所有课程",
            "📝 选择单个课程评价",
            "❌ 退出程序"
        ))

        when (choice) {
            1 -> evaluateAll(currentPeriod, courses)
            2 -> evaluateSingle(currentPeriod, courses)
            3 -> {
                UI.info("感谢使用，再见！")
                return
            }
            else -> {
                UI.warning("无效选择")
                return
            }
        }
    }

    /**
     * 账号密码登录
     */
    private fun passwordLogin(): Boolean {
        UI.section("🔑 账号密码登录")
        UI.info("请输入您的教务系统账号信息")
        println()

        val username = UI.prompt("学号")
        val password = UI.promptPassword("密码")

        println()
        return httpClient.login(username, password)
    }

    /**
     * 二维码登录
     */
    private fun qrCodeLogin(): Boolean {
        UI.section("📱 扫码登录")

        // 获取 UUID
        UI.info("正在生成二维码...")
        val uuid = httpClient.getQrLoginUUID()
        if (uuid == null) {
            UI.error("获取二维码失败")
            if (UI.confirm("是否切换到账号密码登录？")) {
                return passwordLogin()
            }
            return false
        }

        // 显示二维码
        println()
        UI.info("请使用「i轻工大」APP 扫描以下二维码：")
        println()

        // 生成二维码 URL
        val qrUrl = "https://iapp.zzuli.edu.cn/portal/login/appLogin?tourl=https://iapp.zzuli.edu.cn/portal/portal-app/authorize.html?uuid=$uuid"

        // 在终端中显示二维码（使用 ASCII 字符）
        printQRCode(qrUrl)

        println()
        UI.info("${Colors.CYAN}或手动访问：${Colors.RESET}")
        println("   ${Colors.GRAY}$qrUrl${Colors.RESET}")
        println()
        UI.info("${Colors.YELLOW}UUID: $uuid${Colors.RESET}")
        UI.info("等待扫码中... (120秒超时)")
        println()

        // 使用 WebSocket 监听扫码结果
        val result = httpClient.listenQrLoginWebSocket(uuid, 120_000L)

        when (result.first) {
            "confirmed" -> {
                // 扫码确认，获取到用户名密码
                val username = result.second["username"] ?: ""
                val password = result.second["password"] ?: ""
                if (username.isNotEmpty() && password.isNotEmpty()) {
                    println()
                    UI.info("正在使用授权信息登录...")
                    // 使用获取到的凭据登录（密码已经是编码后的）
                    return httpClient.loginWithEncodedPassword(username, password)
                }
            }
            "expired" -> {
                UI.warning("二维码已过期")
            }
            "error" -> {
                UI.error("连接失败")
            }
            else -> {
                UI.warning("扫码超时或取消")
            }
        }

        if (UI.confirm("是否切换到账号密码登录？")) {
            return passwordLogin()
        }
        return false
    }

    /**
     * 在终端打印二维码（简化版，提示用户访问链接）
     */
    private fun printQRCode(url: String) {
        // 使用简单的 ASCII 艺术提示
        println("   ${Colors.WHITE}┌────────────────────────────────────┐${Colors.RESET}")
        println("   ${Colors.WHITE}│                                    │${Colors.RESET}")
        println("   ${Colors.WHITE}│   ${Colors.CYAN}📱 请使用 i轻工大 APP 扫码 📱${Colors.RESET}   ${Colors.WHITE}│${Colors.RESET}")
        println("   ${Colors.WHITE}│                                    │${Colors.RESET}")
        println("   ${Colors.WHITE}│   ${Colors.GRAY}由于终端限制，请复制下方链接${Colors.RESET}   ${Colors.WHITE}│${Colors.RESET}")
        println("   ${Colors.WHITE}│   ${Colors.GRAY}到浏览器生成二维码后扫描：${Colors.RESET}     ${Colors.WHITE}│${Colors.RESET}")
        println("   ${Colors.WHITE}│                                    │${Colors.RESET}")
        println("   ${Colors.WHITE}└────────────────────────────────────┘${Colors.RESET}")
        println()

        // 提供在线二维码生成链接
        val qrGeneratorUrl = "https://api.qrserver.com/v1/create-qr-code/?size=200x200&data=${URLEncoder.encode(url, "UTF-8")}"
        UI.info("${Colors.CYAN}在线生成二维码：${Colors.RESET}")
        println("   ${Colors.BLUE}$qrGeneratorUrl${Colors.RESET}")
    }

    private fun evaluateAll(period: EvaluationPeriod, courses: List<Course>) {
        UI.section("⭐ 开始一键满分评价")

        if (!UI.confirm("确认对所有 ${courses.size} 门课程进行满分评价？")) {
            UI.info("操作已取消")
            return
        }

        println()
        var successCount = 0
        var failCount = 0

        courses.forEachIndexed { index, course ->
            UI.progress(index + 1, courses.size, course.courseName.take(15))

            val success = evaluationService.submitEvaluation(period, course)
            if (success) {
                successCount++
            } else {
                failCount++
            }

            // 添加延时，避免请求过快
            Thread.sleep(500)
        }

        UI.progressDone()
        println()
        UI.divider()
        println()

        if (failCount == 0) {
            UI.success("全部评价完成！成功: $successCount 门 🎉")
        } else {
            UI.warning("评价完成。成功: $successCount 门，失败: $failCount 门")
        }

        println()
        UI.info("提示：请登录教务系统确认评价结果")
    }

    private fun evaluateSingle(period: EvaluationPeriod, courses: List<Course>) {
        UI.section("📝 选择课程")
        println()
        courses.forEachIndexed { index, course ->
            UI.courseItem(index + 1, course)
        }
        println()

        val choice = UI.prompt("请输入课程序号").toIntOrNull() ?: 0
        if (choice < 1 || choice > courses.size) {
            UI.warning("无效的课程序号")
            return
        }

        val course = courses[choice - 1]
        println()
        UI.info("已选择: ${course.courseName} - ${course.teacherName}")

        if (!UI.confirm("确认对该课程进行满分评价？")) {
            UI.info("操作已取消")
            return
        }

        println()
        var success = false
        UI.spinner("正在提交评价...") {
            success = evaluationService.submitEvaluation(period, course)
        }

        if (success) {
            UI.success("评价成功！")
        } else {
            UI.error("评价失败，请稍后重试")
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// Entry Point
// ═══════════════════════════════════════════════════════════════
fun main(args: Array<String>) {
    // 通过 -Dorg.slf4j.simpleLogger.defaultLogLevel=debug 来设置日志级别
    // 或者修改 src/main/resources/logback.xml 配置文件

    println()
    println("${Colors.GRAY}════════════════════════════════════════════════════════════════${Colors.RESET}")
    println("${Colors.WHITE}${Colors.BOLD}  ZZULI 教学评价自动化工具 v1.0${Colors.RESET}")
    println("${Colors.GRAY}  By ShiYi | iOS Design Aesthetic${Colors.RESET}")
    println("${Colors.GRAY}════════════════════════════════════════════════════════════════${Colors.RESET}")
    println()

    try {
        AutoEvaluationApp().run()
    } catch (e: Exception) {
        logger.error(e) { "程序发生错误" }
        UI.error("程序发生错误: ${e.message}")
    }

    println()
    println("${Colors.GRAY}════════════════════════════════════════════════════════════════${Colors.RESET}")
}

