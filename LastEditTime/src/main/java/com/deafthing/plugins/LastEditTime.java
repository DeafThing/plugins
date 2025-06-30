package com.deafthing.plugins;

import android.annotation.SuppressLint;
import android.content.Context;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import java.util.regex.Pattern;

import com.aliucord.Utils;
import com.aliucord.annotations.AliucordPlugin;
import com.aliucord.entities.Plugin;
import com.aliucord.patcher.Hook;
import com.discord.models.message.Message;
import com.discord.utilities.textprocessing.node.EditedMessageNode;
import com.discord.utilities.time.ClockFactory;
import com.discord.utilities.time.TimeUtils;
import com.discord.utilities.view.text.SimpleDraweeSpanTextView;
import com.discord.widgets.chat.list.adapter.WidgetChatListAdapterItemMessage;
import com.discord.widgets.chat.list.entries.MessageEntry;
import com.facebook.drawee.span.DraweeSpanStringBuilder;
import com.lytefast.flexinput.R;

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
                    var editedTimestamp = message.getEditedTimestamp();
                    if (editedTimestamp == null || editedTimestamp.g() <= 0) return;
                    
                    var textView = (SimpleDraweeSpanTextView) param.args[0];
                    var builder = (DraweeSpanStringBuilder) mDraweeStringBuilder.get(textView);
                    if (builder == null) return;
                    
                    var context = textView.getContext();
                    var rawTimestamp = editedTimestamp.g();
                    var readableTime = TimeUtils.toReadableTimeString(context, rawTimestamp, clock).toString();
                    replaceEditedText(context, builder, readableTime);
                    textView.setDraweeSpanStringBuilder(builder);
                    
                } catch (Throwable e) {
                    Utils.log("LastEditTime - Error processing message text: " + e.getMessage());
                }
            })
        );
    }
    
    private void replaceEditedText(Context context, SpannableStringBuilder builder, String readableTime) {
        String text = builder.toString();
        String editedString = context.getString(R.h.message_edited);
        String[] patterns = {
            "\\(" + Pattern.quote(editedString) + "\\)",
            "(?<=\\s)\\(" + Pattern.quote(editedString) + "\\)(?=\\s|$)",
            "(?<=^|\\s)\\(" + Pattern.quote(editedString) + "\\)(?=\\s|$)"
        };
        Utils.log("LastEditTime - Text: '" + text + "'");
        Utils.log("LastEditTime - Looking for: '" + editedString + "'");
        
        for (String patternStr : patterns) {
            java.util.regex.Matcher matcher = Pattern.compile(patternStr).matcher(text);
            if (matcher.find()) {
                int start = matcher.start();
                int end = matcher.end();
                String customEditText = " (" + editedString + ": " + readableTime + ")";
                
                Utils.log("LastEditTime - Found match with pattern: " + patternStr);
                Utils.log("LastEditTime - Replacing '" + text.substring(start, end) + "' with '" + customEditText + "'");
                
                builder.replace(start, end, customEditText);
                int newStart = start;
                int newEnd = start + customEditText.length();
                builder.setSpan(new RelativeSizeSpan(1.0f), newStart, newEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                try {
                    var editedColor = EditedMessageNode.Companion.access$getForegroundColorSpan(EditedMessageNode.Companion, context);
                    builder.setSpan(editedColor, newStart, newEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                } catch (Throwable e) {
                    var grayColor = new ForegroundColorSpan(0xFF888888);
                    builder.setSpan(grayColor, newStart, newEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                return;
            }
        }
        
        Utils.log("LastEditTime - No match found for any pattern");
    }
}