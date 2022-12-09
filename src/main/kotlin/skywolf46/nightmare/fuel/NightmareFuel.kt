package skywolf46.nightmare.fuel

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason
import com.sedmelluq.discord.lavaplayer.track.playback.NonAllocatingAudioFrameBuffer
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.withLock

class NightmareFuel(val playList: MutableList<AudioTrack>) : AudioEventAdapter() {
    companion object {
        val PLAYER_MANAGER = DefaultAudioPlayerManager()

        init {
            PLAYER_MANAGER.configuration.setFrameBufferFactory { bufferDuration, format, stopping ->
                NonAllocatingAudioFrameBuffer(bufferDuration, format, stopping)
            }
            AudioSourceManagers.registerRemoteSources(PLAYER_MANAGER)
            AudioSourceManagers.registerLocalSource(PLAYER_MANAGER)
        }

    }

    private val player: AudioPlayer = PLAYER_MANAGER.createPlayer()

    private val lock = ReentrantReadWriteLock()

    private val pointer = AtomicInteger(-1)

    val handler = NightmareSendHandler(player)


    init {
        player.addListener(this)
        playNext()
    }

    fun queue(track: AudioTrack) {
        lock.writeLock().withLock {
            playList += track
        }
    }

    fun cleanUp() {
        player.destroy()
    }

    fun queue(url: String, afterRun: (Boolean) -> Unit) {
        PLAYER_MANAGER.loadItem(url, object : AudioLoadResultHandler {
            override fun trackLoaded(track: AudioTrack) {
                queue(track)
                afterRun(true)
            }

            override fun playlistLoaded(playlist: AudioPlaylist?) {
                // No support
            }

            override fun noMatches() {
                return afterRun(false)
            }

            override fun loadFailed(exception: FriendlyException?) {
                afterRun(false)
            }

        })
    }

    fun playNext() {
        if (playList.isEmpty())
            return
        val nextPointer = pointer.incrementAndGet()
        if (nextPointer >= playList.size) {
            pointer.set(-1)
            playNext()
            return
        }
        val track = lock.readLock().withLock {
            playList[nextPointer]
        }
        player.playTrack(track.makeClone())
    }

    override fun onTrackEnd(player: AudioPlayer?, track: AudioTrack?, endReason: AudioTrackEndReason) {
        playNext()
    }

    fun checkAndPlay() {
        if (pointer.get() == -1)
            try {
                playNext()
            } catch (e: Throwable) {
                // Do nothing
            }
    }


}