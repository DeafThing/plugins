package com.deafthing.plugins;

import android.annotation.SuppressLint;
import android.content.Context;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;

import com.aliucord.Utils;
import com.aliucord.annotations.AliucordPlugin;
import com.aliucord.entities.Plugin;
import com.aliucord.patcher.Hook;
import com.discord.models.message.Message;
import com.discord.utilities.color.ColorCompat;
import com.discord.utilities.textprocessing.node.EditedMessageNode;
import com.discord.utilities.time.ClockFactory;
import com.discord.utilities.time.TimeUtils;
import com.discord.utilities.view.text.SimpleDraweeSpanTextView;
import com.discord.widgets.chat.list.adapter.WidgetChatListAdapterItemMessage;
import com.discord.widgets.chat.list.entries.MessageEntry;
import com.facebook.drawee.span.DraweeSpanStringBuilder;

import java.lang.reflect.Field;

@AliucordPlugin
@SuppressLint("UseCompatLoadingForDrawables")
public final class LastEditTime extends Plugin {

    @Override
    public void start(Context context) throws Throwable {
        patchProcessMessageText();
    }

    @Override
    public void stop(Context context) {
        patcher.unpatchAll();
    }

    private void patchProcessMessageText() throws Throwable {
        var clock = ClockFactory.get();
        
        // Get the private mDraweeStringBuilder field from SimpleDraweeSpanTextView
        var mDraweeStringBuilder = SimpleDraweeSpanTextView.class.getDeclaredField("mDraweeStringBuilder");
        mDraweeStringBuilder.setAccessible(true);

        patcher.patch(
            WidgetChatListAdapterItemMessage.class, 
            "processMessageText", 
            new Class<?>[]{ SimpleDraweeSpanTextView.class, MessageEntry.class }, 
            new Hook(param -> {
                try {
                    var messageEntry = (MessageEntry) param.args[1];
                    var message = messageEntry.getMessage();
                    
                    if (message == null) return;
                    
                    // Check if message has been edited
                    var editedTimestamp = message.getEditedTimestamp();
                    if (editedTimestamp == null || editedTimestamp.g() <= 0) return;
                    
                    var textView = (SimpleDraweeSpanTextView) param.args[0];
                    var builder = (DraweeSpanStringBuilder) mDraweeStringBuilder.get(textView);
                    if (builder == null) return;
                    
                    var context = textView.getContext();
                    
                    // Get the raw timestamp value
                    var rawTimestamp = editedTimestamp.g();
                    
                    // Convert to readable time
                    var readableTime = TimeUtils.toReadableTimeString(context, rawTimestamp, clock);
                    
                    // Create the edit timestamp text
                    var editText = " (edited: " + readableTime + ")";
                    
                    // Add the edit timestamp to the message
                    addEditTimestamp(context, builder, editText);
                    
                    // Update the text view
                    textView.setDraweeSpanStringBuilder(builder);
                    
                } catch (Throwable e) {
                    // Log error but don't crash
                    Utils.log("LastEditTime - Error processing message text: " + e.getMessage());
                }
            })
        );
    }
    
    private void addEditTimestamp(Context context, SpannableStringBuilder builder, String text) {
        int startPos = builder.length();
        builder.append(text);
        int endPos = builder.length();
        
        // Make the text smaller (75% of normal size)
        builder.setSpan(new RelativeSizeSpan(0.75f), startPos, endPos, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        
        // Apply the same color as Discord's native edited text
        try {
            var editedColor = EditedMessageNode.Companion.access$getForegroundColorSpan(EditedMessageNode.Companion, context);
            builder.setSpan(editedColor, startPos, endPos, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        } catch (Throwable e) {
            // Fallback to a gray color if we can't get Discord's color
            var grayColor = new ForegroundColorSpan(0xFF888888);
            builder.setSpan(grayColor, startPos, endPos, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }
}