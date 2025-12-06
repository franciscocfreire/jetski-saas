/**
 * Notification Sounds using Web Audio API
 *
 * Generates alert sounds without external audio files
 */

let audioContext: AudioContext | null = null

function getAudioContext(): AudioContext {
  if (!audioContext) {
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const AudioContextClass = window.AudioContext || (window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext
    audioContext = new AudioContextClass()
  }
  return audioContext
}

export type SoundType = 'warning' | 'expired' | 'success'

/**
 * Play a notification sound
 * @param type The type of sound to play
 */
export async function playNotificationSound(type: SoundType): Promise<void> {
  try {
    const ctx = getAudioContext()

    // Resume context if suspended (browser autoplay policy)
    if (ctx.state === 'suspended') {
      await ctx.resume()
    }

    const oscillator = ctx.createOscillator()
    const gainNode = ctx.createGain()

    oscillator.connect(gainNode)
    gainNode.connect(ctx.destination)

    switch (type) {
      case 'warning':
        // Attention-grabbing double beep - louder and longer
        oscillator.frequency.value = 880
        oscillator.type = 'sine'
        gainNode.gain.setValueAtTime(0.5, ctx.currentTime)
        gainNode.gain.exponentialRampToValueAtTime(0.01, ctx.currentTime + 0.2)
        gainNode.gain.setValueAtTime(0.5, ctx.currentTime + 0.3)
        gainNode.gain.exponentialRampToValueAtTime(0.01, ctx.currentTime + 0.5)
        oscillator.start(ctx.currentTime)
        oscillator.stop(ctx.currentTime + 0.6)
        break

      case 'expired':
        // URGENT alarm - alternating frequencies, loud and persistent
        oscillator.type = 'square'
        // Siren effect - alternating between two frequencies
        oscillator.frequency.setValueAtTime(800, ctx.currentTime)
        oscillator.frequency.setValueAtTime(1200, ctx.currentTime + 0.15)
        oscillator.frequency.setValueAtTime(800, ctx.currentTime + 0.3)
        oscillator.frequency.setValueAtTime(1200, ctx.currentTime + 0.45)
        oscillator.frequency.setValueAtTime(800, ctx.currentTime + 0.6)
        oscillator.frequency.setValueAtTime(1200, ctx.currentTime + 0.75)
        // Loud and sustained
        gainNode.gain.setValueAtTime(0.6, ctx.currentTime)
        gainNode.gain.setValueAtTime(0.6, ctx.currentTime + 0.9)
        gainNode.gain.exponentialRampToValueAtTime(0.01, ctx.currentTime + 1.0)
        oscillator.start(ctx.currentTime)
        oscillator.stop(ctx.currentTime + 1.1)
        break

      case 'success':
        // Pleasant ascending tone
        oscillator.frequency.value = 440
        oscillator.frequency.setValueAtTime(440, ctx.currentTime)
        oscillator.frequency.exponentialRampToValueAtTime(880, ctx.currentTime + 0.2)
        oscillator.type = 'sine'
        gainNode.gain.setValueAtTime(0.4, ctx.currentTime)
        gainNode.gain.exponentialRampToValueAtTime(0.01, ctx.currentTime + 0.3)
        oscillator.start(ctx.currentTime)
        oscillator.stop(ctx.currentTime + 0.35)
        break
    }
  } catch (error) {
    console.warn('Could not play notification sound:', error)
  }
}

/**
 * Request permission to play sounds (must be called from user interaction)
 */
export async function initializeSounds(): Promise<boolean> {
  try {
    const ctx = getAudioContext()
    if (ctx.state === 'suspended') {
      await ctx.resume()
    }
    return true
  } catch (error) {
    console.warn('Could not initialize audio context:', error)
    return false
  }
}
