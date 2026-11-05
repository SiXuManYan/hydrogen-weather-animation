package com.sugar.weather.ui.about

import androidx.annotation.StringRes
import com.sugar.weather.R

/**
 * 允许应用打开的离线文档清单，不接受 Intent 传入任意文件路径。
 *
 * @property assetPath 构建时从仓库同步的 Markdown 路径。
 * @property titleRes 页面标题。
 */
enum class LocalDocument(val assetPath: String, @param:StringRes val titleRes: Int) {
    USER_AGREEMENT("legal/USER_AGREEMENT.md", R.string.about_user_agreement),
    PRIVACY_POLICY("legal/PRIVACY_POLICY.md", R.string.about_privacy_policy),
    CHANGELOG("history/CHANGELOG.md", R.string.about_changelog_title),
    USER_GUIDE("help/USER_GUIDE.md", R.string.about_user_guide),
    THIRD_PARTY_NOTICES("help/THIRD_PARTY_NOTICES.md", R.string.about_third_party);
}
