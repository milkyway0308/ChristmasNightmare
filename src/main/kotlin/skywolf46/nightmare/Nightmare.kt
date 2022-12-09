package skywolf46.nightmare

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.entities.Activity
import net.dv8tion.jda.api.requests.GatewayIntent
import skywolf46.nightmare.fuel.NightmareFuel
import skywolf46.nightmare.listener.CommandListener
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.sql.Connection
import java.sql.DriverManager
import java.util.*
import kotlin.system.exitProcess


object Nightmare {
    private lateinit var connection: Connection

    val playList: MutableList<AudioTrack> = Collections.synchronizedList(mutableListOf<AudioTrack>())
    val urlList: MutableList<String> = Collections.synchronizedList(mutableListOf<String>())

    var adminId = ""
        private set

    @JvmStatic
    fun main(args: Array<String>) {
        init()
    }


    private fun init() {
        println("-- Configuration load start")
        val file = File("config.properties")
        val prop = Properties()
        checkConfiguration(prop, file)
        loadConfiguration(prop, file)
        setupSql()
        println("-- Configuration load complete")
        startBot(prop)
    }

    private fun checkConfiguration(properties: Properties, file: File) {
        properties["token"] = "Add discord bot token here"
        properties["adminId"] = "Add admin discord id here"

        if (!file.exists()) {
            println("..Configuration not found. Creating new one.")
            runCatching {
                file.createNewFile()
                FileOutputStream(file).use {
                    properties.store(it, "Nightmare configuration")
                }
            }.onFailure {
                System.err.println("....Failed to create new configuration")
                it.printStackTrace()
                exitProcess(-1)
            }
            println("....New configuration saved on \"${file.path}\"")
        }
    }


    private fun loadConfiguration(properties: Properties, file: File) {
        println("..Loading configuration")
        runCatching {
            FileInputStream(file).use {
                properties.load(it)
            }
            adminId = properties.getProperty("adminId")
        }.onFailure {
            System.err.println("....Failed to load configuration")
            it.printStackTrace()
            exitProcess(-1)
        }
    }

    private fun setupSql() {
        println("..Setup Sql")
        Class.forName("org.sqlite.JDBC")
        val targetFile = File("playList.db")
        if (!targetFile.exists() && targetFile.parentFile != null)
            targetFile.parentFile.mkdirs()
        targetFile.createNewFile()
        connection = DriverManager.getConnection("jdbc:sqlite:" + targetFile.absolutePath.replace("\\", "\\\\"))
        connection.prepareStatement("create table if not exists carolSongs (url TEXT)").use {
            it.execute()
        }
        var size = 0
        connection.prepareStatement("select * from carolSongs").use {
            it.executeQuery().use { set ->
                while (set.next()) {
                    queue(set.getString(1), false)
                    println(set.getString(1))
                    size++
                }
            }
        }
        println("Loaded ${size} songs")
    }

    private fun startBot(prop: Properties) {
        println("-- JDA initialization start")
        val jda = initJda(prop)
        initCommands(prop, jda)
        println("-- JDA initialization complete")
    }

    private fun initJda(prop: Properties): JDA {
        return try {
            JDABuilder.create(
                prop.getProperty("token"),
                GatewayIntent.GUILD_MESSAGE_REACTIONS,
                GatewayIntent.GUILD_VOICE_STATES
            )
                .addEventListeners(CommandListener())
                .setActivity(Activity.playing("Nightmare"))
                .build()
        } catch (e: Throwable) {
            System.err.println("....Failed to initialize JDA")
            e.printStackTrace()
            exitProcess(-1)
        }
    }

    private fun initCommands(prop: Properties, jda: JDA) {
        jda.upsertCommand("carol", "끔찍한 캐롤 송을 추가합니다. 봇 관리자 외에는 못쓰니 안심하십시오.").queue()
        jda.upsertCommand("invite-carol", "현재 채널에 캐롤을 재생할 봇을 초대합니다. 봇은 꺼지거나 추방 전까진 절대로 안나갑니다.").queue()
    }

    fun dequeue(index: Int) {
        val url = urlList[index]
        urlList.removeAt(index)
        playList.removeAt(index)
        connection.prepareStatement("delete from carolSongs where url = ?").use {
            it.setString(1, url)
            it.execute()
        }
    }

    fun queue(url: String, save: Boolean, afterRun: (Boolean) -> Unit = {}) {
        if (save) {
            connection.prepareStatement("insert into carolSongs values(?)").use {
                it.setString(1, url)
                it.execute()
            }
        }
        urlList.add(url)
        NightmareFuel.PLAYER_MANAGER.loadItem(url, object : AudioLoadResultHandler {
            override fun trackLoaded(track: AudioTrack) {
                playList.add(track)
                afterRun(true)
            }

            override fun playlistLoaded(playlist: AudioPlaylist) {
                playList.addAll(playList)
            }

            override fun noMatches() {
                return afterRun(false)
            }

            override fun loadFailed(exception: FriendlyException?) {
                afterRun(false)
            }

        })
    }

    fun newNightmare(): NightmareFuel {
        return NightmareFuel(playList)
    }

    fun nightmareSize(): Int {
        return playList.size
    }

}
