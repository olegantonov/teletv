package org.danielmarques.teletv

import android.content.Context
import org.drinkless.tdlib.TdApi
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.InputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.security.SecureRandom
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Página de configuração servida pela própria TV na rede local: o celular lê o QR code,
 * abre a página e digita ali a chave de API, o número, o código e a senha.
 * Só fica no ar enquanto a tela de login está aberta, e o endereço leva um código aleatório.
 */
object Ponte {
    /** A página começa pedindo a chave de API mesmo que o app já tenha uma. */
    @Volatile var pedirApi = false

    private var servidor: ServerSocket? = null
    private var segredo = ""
    private lateinit var app: Context

    /** Devolve o endereço para o QR code, ou null se a TV está sem rede. */
    fun iniciar(ctx: Context): String? {
        app = ctx.applicationContext
        val ip = ipLocal() ?: return null
        parar()
        segredo = ByteArray(6).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
        val s = try {
            ServerSocket(8765)
        } catch (_: Exception) {
            ServerSocket(0)
        }
        servidor = s
        thread(isDaemon = true) {
            while (!s.isClosed) {
                val cliente = try {
                    s.accept()
                } catch (_: Exception) {
                    break
                }
                thread(isDaemon = true) {
                    try {
                        cliente.use(::atender)
                    } catch (_: Exception) {
                    }
                }
            }
        }
        return "http://$ip:${s.localPort}/$segredo"
    }

    fun parar() {
        try {
            servidor?.close()
        } catch (_: Exception) {
        }
        servidor = null
    }

    private fun ipLocal(): String? = NetworkInterface.getNetworkInterfaces().toList()
        .filter { it.isUp && !it.isLoopback }
        .flatMap { it.inetAddresses.toList() }
        .firstOrNull { it is Inet4Address && it.isSiteLocalAddress }
        ?.hostAddress

    private fun linha(e: InputStream): String {
        val sb = StringBuilder()
        while (true) {
            val c = e.read()
            if (c < 0 || c == '\n'.code) break
            if (c != '\r'.code) sb.append(c.toChar())
        }
        return sb.toString()
    }

    private fun atender(s: Socket) {
        s.soTimeout = 15000
        val e = BufferedInputStream(s.getInputStream())
        val pedido = linha(e).split(' ')
        if (pedido.size < 2) return
        var tamanho = 0
        while (true) {
            val l = linha(e)
            if (l.isEmpty()) break
            if (l.startsWith("content-length:", ignoreCase = true)) tamanho = l.substringAfter(':').trim().toIntOrNull() ?: 0
        }
        val corpo = ByteArray(tamanho.coerceIn(0, 4096))
        var lidos = 0
        while (lidos < corpo.size) {
            val n = e.read(corpo, lidos, corpo.size - lidos)
            if (n < 0) break
            lidos += n
        }
        val campos = String(corpo, 0, lidos).split('&').filter { '=' in it }.associate {
            URLDecoder.decode(it.substringBefore('='), "UTF-8") to URLDecoder.decode(it.substringAfter('='), "UTF-8")
        }
        val caminho = pedido[1].substringBefore('?')
        val base = "/$segredo"
        when {
            !caminho.startsWith(base) -> responder(s, 404, "text/plain", "404")
            pedido[0] == "GET" && caminho == base -> responder(s, 200, "text/html", pagina())
            pedido[0] == "GET" && caminho == "$base/state" -> responder(s, 200, "application/json", estado())
            pedido[0] == "POST" -> responder(s, 200, "application/json", acao(caminho.removePrefix("$base/"), campos))
            else -> responder(s, 404, "text/plain", "404")
        }
    }

    private fun responder(s: Socket, codigo: Int, tipo: String, corpo: String) {
        val bytes = corpo.toByteArray()
        val cabecalho = "HTTP/1.1 $codigo OK\r\nContent-Type: $tipo; charset=utf-8\r\nContent-Length: ${bytes.size}\r\n" +
            "Cache-Control: no-store\r\nConnection: close\r\n\r\n"
        s.getOutputStream().apply {
            write(cabecalho.toByteArray())
            write(bytes)
            flush()
        }
    }

    private fun estado(): String {
        val a = Tg.auth
        val passo = when {
            !Tg.configurado || pedirApi -> "api"
            a is TdApi.AuthorizationStateWaitPhoneNumber -> "phone"
            a is TdApi.AuthorizationStateWaitCode -> "code"
            a is TdApi.AuthorizationStateWaitPassword -> "password"
            a is TdApi.AuthorizationStateWaitOtherDeviceConfirmation -> "qr"
            a is TdApi.AuthorizationStateReady -> "ready"
            else -> "wait"
        }
        return JSONObject()
            .put("step", passo)
            .put("hint", (a as? TdApi.AuthorizationStateWaitPassword)?.passwordHint ?: "")
            .put("builtin", Tg.temChaveEmbutida)
            .toString()
    }

    private fun ok() = """{"ok":true}"""

    private fun erro(msg: String?) = JSONObject().put("ok", false).put("error", msg ?: "?").toString()

    private fun acao(nome: String, c: Map<String, String>): String = when (nome) {
        "api" -> {
            val id = c["id"]?.trim()?.toIntOrNull()
            val hash = c["hash"]?.trim()?.lowercase().orEmpty()
            if (id == null || id <= 0 || !Regex("[0-9a-f]{32}").matches(hash)) {
                erro(app.getString(R.string.web_invalid))
            } else {
                Prefs.definirApi(id, hash)
                pedirApi = false
                Tg.trocarApi()
                ok()
            }
        }
        "builtin" -> {
            if (Prefs.apiId != 0) {
                Prefs.definirApi(0, "")
                Tg.trocarApi()
            }
            pedirApi = false
            ok()
        }
        "ownkey" -> {
            pedirApi = true
            ok()
        }
        "phone" -> telegram(TdApi.SetAuthenticationPhoneNumber(c["v"].orEmpty().filter { it.isDigit() }, null))
        "code" -> telegram(TdApi.CheckAuthenticationCode(c["v"].orEmpty().trim()))
        "password" -> telegram(TdApi.CheckAuthenticationPassword(c["v"].orEmpty()))
        else -> erro("?")
    }

    /** Espera a resposta da TDLib para devolver o erro (código errado, senha errada) à página. */
    private fun telegram(q: TdApi.Function<*>): String {
        val pronto = CountDownLatch(1)
        var resposta: TdApi.Object? = null
        Tg.enviar(q) {
            resposta = it
            pronto.countDown()
        }
        pronto.await(12, TimeUnit.SECONDS)
        val r = resposta
        return if (r is TdApi.Error) erro(r.message) else ok()
    }

    private fun t(id: Int) = app.getString(id).replace("&", "&amp;").replace("<", "&lt;")

    private fun pagina(): String = """<!doctype html>
<html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>${t(R.string.web_title)}</title>
<style>
body{margin:0;font:17px/1.45 system-ui,sans-serif;background:#10151c;color:#f2f5f8}
main{max-width:460px;margin:0 auto;padding:24px 20px}
h1{font-size:22px;color:#2aabee;margin:0 0 6px}h2{font-size:19px;margin:22px 0 10px}
ol{padding-left:22px;margin:0 0 14px}li{margin-bottom:8px}
input{width:100%;box-sizing:border-box;padding:14px;margin:6px 0;border-radius:10px;border:1px solid #33414f;background:#1b232e;color:#f2f5f8;font-size:18px}
button,a.b{display:block;width:100%;box-sizing:border-box;padding:14px;margin:10px 0;border:0;border-radius:10px;background:#2aabee;color:#fff;font-size:18px;font-weight:600;text-align:center;text-decoration:none}
button.s,a.s{background:#1b232e;color:#8e9baa;font-weight:400}
#e{color:#ff8a80;min-height:24px}.h{color:#8e9baa}section{display:none}
</style></head><body><main>
<h1>${t(R.string.web_title)}</h1><div id="e"></div>
<section id="api"><h2>${t(R.string.web_api_title)}</h2>
<ol><li>${t(R.string.web_api_1)}</li><li>${t(R.string.web_api_2)}</li><li>${t(R.string.web_api_3)}</li></ol>
<a class="b s" href="https://my.telegram.org" target="_blank" rel="noopener">${t(R.string.web_open_site)}</a>
<input id="id" inputmode="numeric" placeholder="api_id" autocomplete="off">
<input id="hash" placeholder="api_hash" autocomplete="off" autocapitalize="off" spellcheck="false">
<button onclick="p('api','id='+v('id')+'&hash='+v('hash'))">${t(R.string.web_save)}</button>
<button class="s" id="bi" onclick="p('builtin','')">${t(R.string.web_use_builtin)}</button></section>
<section id="phone"><h2>${t(R.string.web_phone_title)}</h2>
<input id="ph" type="tel" placeholder="${t(R.string.web_phone_hint)}" autocomplete="tel">
<button onclick="p('phone','v='+v('ph'))">${t(R.string.web_send)}</button>
<button class="s" onclick="p('ownkey','')">${t(R.string.web_own_key)}</button></section>
<section id="code"><h2>${t(R.string.web_code_title)}</h2>
<input id="co" inputmode="numeric" placeholder="${t(R.string.web_code_hint)}" autocomplete="one-time-code">
<button onclick="p('code','v='+v('co'))">${t(R.string.web_confirm)}</button></section>
<section id="password"><h2>${t(R.string.web_password_title)}</h2><div class="h" id="hint"></div>
<input id="pw" type="password" placeholder="${t(R.string.web_password_hint)}">
<button onclick="p('password','v='+v('pw'))">${t(R.string.web_enter)}</button></section>
<section id="qr"><p>${t(R.string.web_qr)}</p></section>
<section id="ready"><p>${t(R.string.web_ready)}</p></section>
<section id="wait"><p class="h">${t(R.string.web_wait)}</p></section>
</main><script>
var base=location.pathname.replace(/\/$/,''),atual='';
function v(i){return encodeURIComponent(document.getElementById(i).value)}
function erro(m){document.getElementById('e').textContent=m||''}
function mostrar(s){if(s.step!==atual){atual=s.step;erro('');
var l=document.getElementsByTagName('section');for(var i=0;i<l.length;i++)l[i].style.display=l[i].id===atual?'block':'none'}
document.getElementById('hint').textContent=s.hint?'${t(R.string.web_hint)}'+s.hint:'';
document.getElementById('bi').style.display=s.builtin?'block':'none'}
function ler(){fetch(base+'/state').then(function(r){return r.json()}).then(mostrar).catch(function(){erro('${t(R.string.web_lost).replace("'", "\\'")}')})}
function p(a,d){erro('');fetch(base+'/'+a,{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:d})
.then(function(r){return r.json()}).then(function(r){if(!r.ok)erro(r.error);ler()}).catch(function(){erro('${t(R.string.web_lost).replace("'", "\\'")}')})}
ler();setInterval(ler,2000);
</script></body></html>"""
}
