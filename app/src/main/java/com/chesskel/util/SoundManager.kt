// kotlin
package com.chesskel.util

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import androidx.collection.SparseArrayCompat
import com.chesskel.R

object SoundManager {

    enum class Sound {
        CAPTURE, CASTLE, CHECK, GAMEOVER, MOVE, PROMOTE, SELECT
    }

    private var soundPool: SoundPool? = null
    private val soundMap = mutableMapOf<Sound, Int>()
    private val loadedSamples = mutableSetOf<Int>()
    private val pendingPlays = mutableMapOf<Int, MutableList<Float>>() // sampleId -> volumes

    /**
     * Initialize SoundPool and load samples. Safe to call multiple times.
     */
    fun init(context: Context) {
        if (soundPool != null) return

        val appCtx = context.applicationContext
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setAudioAttributes(attrs)
            .setMaxStreams(6)
            .build()

        // Single listener: mark loaded and play any queued requests for that sample
        soundPool?.setOnLoadCompleteListener { pool, sampleId, status ->
            if (status == 0) {
                loadedSamples.add(sampleId)
                pendingPlays.remove(sampleId)?.forEach { vol ->
                    pool.play(sampleId, vol, vol, 1, 0, 1f)
                }
            }
        }

        fun load(s: Sound, resId: Int) {
            val id = soundPool!!.load(appCtx, resId, 1)
            soundMap[s] = id
        }

        load(Sound.CAPTURE, R.raw.capture)
        load(Sound.CASTLE, R.raw.castle)
        load(Sound.CHECK, R.raw.sound_check)
        load(Sound.GAMEOVER, R.raw.sound_gameover)
        load(Sound.MOVE, R.raw.move)
        load(Sound.PROMOTE, R.raw.promote)
        load(Sound.SELECT, R.raw.select)
    }

    /**
     * Play a sound. If the sample isn't loaded yet, queue it to play once loading completes.
     */
    fun play(sound: Sound, volume: Float = 1f) {
        val sp = soundPool ?: return
        val id = soundMap[sound] ?: return

        if (loadedSamples.contains(id)) {
            sp.play(id, volume, volume, 1, 0, 1f)
            return
        }

        // queue the play for when the sample finishes loading
        val list = pendingPlays.getOrPut(id) { mutableListOf() }
        list.add(volume)
    }

    // Convenience helpers used by code
    fun playMove() = play(Sound.MOVE)
    fun playCapture() = play(Sound.CAPTURE)
    fun playCheck() = play(Sound.CHECK)
    fun playPromote() = play(Sound.PROMOTE)
    fun playGameOver() = play(Sound.GAMEOVER)
    fun playSelect() = play(Sound.SELECT)
    fun playCastle() = play(Sound.CASTLE)

    /**
     * Release resources. Call from Activity/Fragment on destroy.
     */
    fun release() {
        soundPool?.release()
        soundPool = null
        soundMap.clear()
        loadedSamples.clear()
        pendingPlays.clear()
    }
}
