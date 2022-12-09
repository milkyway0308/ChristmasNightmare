package skywolf46.nightmare.listener

import net.dv8tion.jda.api.events.guild.voice.GuildVoiceLeaveEvent
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.interactions.components.Modal
import net.dv8tion.jda.api.interactions.components.text.TextInput
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle
import skywolf46.nightmare.Nightmare
import skywolf46.nightmare.fuel.NightmareFuel
import java.util.*

class CommandListener : ListenerAdapter() {
    private val nightmareStorage = Collections.synchronizedMap(mutableMapOf<Long, NightmareFuel>())

    fun updateAll() {
        nightmareStorage.values.toList().forEach {
            it.checkAndPlay()
        }
    }

    override fun onSlashCommandInteraction(event: SlashCommandInteractionEvent) {
        when (event.name) {
            "carol" -> {
                if (Nightmare.adminId != event.user.id) {
                    event.reply("봇 관리자만 사용이 가능한 명령어입니다.").submit()
                    return
                }
                event.replyModal(
                    Modal.create("carol_fuel", "새 캐롤 추가하기")
                        .addActionRow(TextInput.create("fuel_type", "URL", TextInputStyle.SHORT).build())
                        .build()
                ).submit()
            }

            "list-carol" -> {
                if (Nightmare.adminId != event.user.id) {
                    event.reply("봇 관리자만 사용이 가능한 명령어입니다.")
                    return
                }
                val builder = StringBuilder("현재 등록된 플레이리스트는 다음과 같습니다.\n")
                for (x in Nightmare.playList.indices) {
                    builder.append("[${x + 1}] ${Nightmare.playList[x].sourceManager.sourceName}")
                }
                event.reply(builder.toString()).submit()
            }

            "invite-carol" -> {
                event.member!!.voiceState?.channel?.apply {
                    nightmareStorage.remove(event.guild!!.idLong)?.cleanUp()
                    val audioManager = event.guild!!.audioManager
                    audioManager.sendingHandler =
                        nightmareStorage.getOrPut(event.guild!!.idLong) { Nightmare.newNightmare() }.handler
                    audioManager.openAudioConnection(this)
                    event.reply("초대되었습니다.").submit()
                } ?: kotlin.run {
                    event.reply("채널에 접속한 상태여야 합니다.").submit()
                }

                event.guild!!.voiceStates.filter { it.member.id == event.user.id }.get(0)
            }
        }
    }

    override fun onModalInteraction(event: ModalInteractionEvent) {
        when (event.modalId) {
            "carol_fuel" -> {
                Nightmare.queue(event.getValue("fuel_type")!!.asString, true) {
                    updateAll()
                }
                event.reply("추가되었습니다. 현재 등록된 URL은 ${Nightmare.nightmareSize() + 1}개 입니다.").submit()
            }
        }
    }

    override fun onGuildVoiceLeave(event: GuildVoiceLeaveEvent) {
        val selfId = event.jda.selfUser.idLong
        if (selfId == event.entity.user.idLong) {
            nightmareStorage.remove(event.guild.idLong)?.cleanUp()
        }
    }
}