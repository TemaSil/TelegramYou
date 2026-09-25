package com.telegramyou.app.telegram.privacy

import com.telegramyou.app.telegram.model.PrivacyRules
import com.telegramyou.app.telegram.model.PrivacySetting

/** Who may see what of this account, and who may reach it. */
interface TelegramPrivacy {
    suspend fun privacyRules(setting: PrivacySetting): PrivacyRules

    /**
     * Writes [rules] for [setting]: its exceptions exactly as they were read,
     * then its audience. Exceptions are never dropped by a change made here.
     */
    suspend fun setPrivacyRules(setting: PrivacySetting, rules: PrivacyRules)
}
