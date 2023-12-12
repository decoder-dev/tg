package org.telegram.ui.Components.Premium.boosts.cells.statistics;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.tgnet.tl.TL_stories;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.UserCell;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.Premium.boosts.BoostRepository;

import uz.unnarsx.cherrygram.CherrygramConfig;

@SuppressLint("ViewConstructor")
public class GiveawayCell extends UserCell {
    private CounterDrawable counterDrawable;
    private TL_stories.TL_prepaidGiveaway prepaidGiveaway;

    public GiveawayCell(Context context, int padding, int checkbox, boolean admin) {
        super(context, padding, checkbox, admin);
        init(context);
    }

    public GiveawayCell(Context context, int padding, int checkbox, boolean admin, Theme.ResourcesProvider resourcesProvider) {
        super(context, padding, checkbox, admin, resourcesProvider);
        init(context);
    }

    public GiveawayCell(Context context, int padding, int checkbox, boolean admin, boolean needAddButton) {
        super(context, padding, checkbox, admin, needAddButton);
        init(context);
    }

    public GiveawayCell(Context context, int padding, int checkbox, boolean admin, boolean needAddButton, Theme.ResourcesProvider resourcesProvider) {
        super(context, padding, checkbox, admin, needAddButton, resourcesProvider, false);
        init(context);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (needDivider && !CherrygramConfig.INSTANCE.getDisableDividers()) {
            canvas.drawLine(LocaleController.isRTL ? 0 : AndroidUtilities.dp(70), getMeasuredHeight() - 1, getMeasuredWidth() - (LocaleController.isRTL ? AndroidUtilities.dp(70) : 0), getMeasuredHeight() - 1, Theme.dividerPaint);
        }
    }

    private void init(Context context) {
        counterDrawable = new CounterDrawable(context);
    }

    public TL_stories.TL_prepaidGiveaway getPrepaidGiveaway() {
        return prepaidGiveaway;
    }

    public void setImage(TL_stories.TL_prepaidGiveaway prepaidGiveaway) {
        this.prepaidGiveaway = prepaidGiveaway;
        avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_GIFT);
        if (prepaidGiveaway.months == 12) {
            avatarDrawable.setColor(0xFFff8560, 0xFFd55246);
        } else if (prepaidGiveaway.months == 6) {
            avatarDrawable.setColor(0xFF5caefa, 0xFF418bd0);
        } else {
            avatarDrawable.setColor(0xFF9ad164, 0xFF49ba44);
        }
        String counterStr = String.valueOf(prepaidGiveaway.quantity * BoostRepository.giveawayBoostsPerPremium());
        counterDrawable.setText(counterStr);
        nameTextView.setRightDrawable(counterDrawable);
    }
}
