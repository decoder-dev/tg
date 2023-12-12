package uz.unnarsx.cherrygram.preferences

import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.BuildConfig
import org.telegram.messenger.BuildVars
import org.telegram.messenger.LocaleController
import org.telegram.messenger.R
import org.telegram.messenger.browser.Browser
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.Components.BulletinFactory
import uz.unnarsx.cherrygram.CherrygramConfig
import uz.unnarsx.cherrygram.crashlytics.Crashlytics
import uz.unnarsx.cherrygram.extras.CherrygramExtras
import uz.unnarsx.cherrygram.ui.tgkit.preference.category
import uz.unnarsx.cherrygram.ui.tgkit.preference.textDetail
import uz.unnarsx.cherrygram.ui.tgkit.preference.textIcon
import uz.unnarsx.cherrygram.ui.tgkit.preference.tgKitScreen
import uz.unnarsx.cherrygram.ui.tgkit.preference.types.TGKitTextDetailRow
import uz.unnarsx.cherrygram.ui.tgkit.preference.types.TGKitTextIconRow
import uz.unnarsx.cherrygram.updater.UpdaterBottomSheet

class AboutPreferencesEntry : BasePreferencesEntry {
    override fun getPreferences(bf: BaseFragment) = tgKitScreen(LocaleController.getString("CGP_Header_About", R.string.CGP_Header_About)) {
        category(LocaleController.getString("Info", R.string.Info)) {
            textDetail {
                title = LocaleController.getString("CG_AppName", R.string.CG_AppName) + " " + CherrygramExtras.CG_VERSION + " | " + "Telegram v" + BuildVars.BUILD_VERSION_STRING + " " + "(" + BuildVars.BUILD_VERSION + ")"
                detail = LocaleController.getString("CGP_About_Desc", R.string.CGP_About_Desc)

                listener = TGKitTextDetailRow.TGTDListener {
                    Browser.openUrl(bf.parentActivity, "https://github.com/arsLan4k1390/Cherrygram#readme")
                }
            }

            textDetail {
                icon = R.drawable.sync_outline_28
                title = LocaleController.getString("UP_Category_Updates", R.string.UP_Category_Updates)
                detail = LocaleController.getString("UP_LastCheck", R.string.UP_LastCheck) + ": " + LocaleController.formatDateTime(CherrygramConfig.lastUpdateCheckTime / 1000);

                listener = TGKitTextDetailRow.TGTDListener {
                    UpdaterBottomSheet(bf.parentActivity, bf, false, null).show()
                }
            }

            textIcon {
                icon = R.drawable.bug_solar
                title = LocaleController.getString("CG_CopyReportDetails", R.string.CG_CopyReportDetails)

                listener = TGKitTextIconRow.TGTIListener {
                    AndroidUtilities.addToClipboard(Crashlytics.getReportMessage().toString() + "\n\n#bug")
                    BulletinFactory.of(bf).createErrorBulletin(LocaleController.getString("CG_ReportDetailsCopied", R.string.CG_ReportDetailsCopied)).show()
                }
            }
        }

        category(LocaleController.getString("CGP_Links", R.string.CGP_Links)) {
            textIcon {
                icon = R.drawable.msg_channel_solar
                title = LocaleController.getString("CGP_ToChannel", R.string.CGP_ToChannel)
                value = "@Cherry_gram"

                listener = TGKitTextIconRow.TGTIListener {
                    Browser.openUrl(bf.parentActivity, "https://t.me/Cherry_gram")
                }
            }
            textIcon {
                icon = R.drawable.msg_discuss_solar
                title = LocaleController.getString("CGP_ToChat", R.string.CGP_ToChat)
                value = "@CherrygramSupport"

                listener = TGKitTextIconRow.TGTIListener {
                    Browser.openUrl(bf.parentActivity, "https://t.me/CherrygramSupport")
                }
            }
            textIcon {
                val commitInfo = "commit " + BuildConfig.GIT_COMMIT_HASH.substring(0, 8)

                icon = R.drawable.github_logo_white
                title = LocaleController.getString("CGP_Source", R.string.CGP_Source)
                if (!BuildVars.isBetaApp()) value = commitInfo

                listener = TGKitTextIconRow.TGTIListener {
                    if (!BuildVars.isBetaApp()) {
                        Browser.openUrl(bf.parentActivity, "https://github.com/arsLan4k1390/Cherrygram/commit/" + BuildConfig.GIT_COMMIT_HASH)
                    } else {
                        Browser.openUrl(bf.parentActivity, "https://github.com/arsLan4k1390/Cherrygram/")
                    }
                }
            }
            textIcon {
                icon = R.drawable.msg_translate_solar
                title = LocaleController.getString("CGP_Crowdin", R.string.CGP_Crowdin)
                value = "Crowdin"

                listener = TGKitTextIconRow.TGTIListener {
                    Browser.openUrl(bf.parentActivity, "https://crowdin.com/project/cherrygram")
                }
            }
            /*textIcon {
                icon = R.drawable.heart_angle_solar
                title = LocaleController.getString("DP_Donate", R.string.DP_Donate)

                listener = TGKitTextIconRow.TGTIListener {
                    it.presentFragment(CherrygramPreferencesNavigator.createDonate())
                }
            }*/
        }

    }
}