package se.oort.diplicity;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.TaskStackBuilder;
import android.content.Context;
import android.content.Intent;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import se.oort.diplicity.apigen.Game;
import se.oort.diplicity.apigen.Member;
import se.oort.diplicity.apigen.SingleContainer;
import se.oort.diplicity.game.GameActivity;
import se.oort.diplicity.game.PressActivity;

// NotificationReceiveActivity receives notifications directly from FCM when the app was
// turned off as the notification arrived (and triggers using the ClickAction), via
// the MessagingService if the app was turned on but no activity consumed that particular
// message and via the Alarm receiver for deadline warnings.
public class NotificationReceiveActivity extends RetrofitActivity {

    public static final String DIPLICITY_JSON_EXTRA = "DiplicityJSON";

    public static void sendNotification(Context context, Intent intent, String title, String body) {
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_ONE_SHOT);

        Uri defaultSoundUri= RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(context)
                .setSmallIcon(R.drawable.ic_otto)
                .setContentTitle(title)
                .setContentText(body)
                .setAutoCancel(true)
                .setSound(defaultSoundUri)
                .setContentIntent(pendingIntent);

        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        notificationManager.notify(0, notificationBuilder.build());
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        Log.d("Diplicity", "Got " + getIntent().getAction());
        if (getIntent().getAction().equals(MessagingService.FCM_NOTIFY_ACTION)) {
            handleDiplicityJSON();
        } else if (getIntent().getAction().equals(Alarm.DEADLINE_WARNING_ACTION)) {
            handleAlarm();
        }
    }

    private void handleAlarm() {
        Log.d("Diplicity", "Got alarm intent!");
        startGameActivity(getIntent().getStringExtra(Alarm.GAME_ID_KEY));
    }

    private void handleDiplicityJSON() {
        final MessagingService.DiplicityJSON message = MessagingService.decodeDataPayload(getIntent().getExtras().getString(DIPLICITY_JSON_EXTRA));
        if (message != null) {
            if (message.type.equals("message")) {
                handleReq(
                        gameService.GameLoad(message.message.GameID),
                        new Sendable<SingleContainer<Game>>() {
                            @Override
                            public void send(SingleContainer<Game> gameSingleContainer) {
                                Member member = getLoggedInMember(gameSingleContainer.Properties);
                                if (member != null) {
                                    Intent mainIntent = new Intent(NotificationReceiveActivity.this, MainActivity.class);
                                    mainIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
                                    Intent gameIntent = GameActivity.startGameIntent(NotificationReceiveActivity.this, gameSingleContainer.Properties, gameSingleContainer.Properties.NewestPhaseMeta.get(0));
                                    ChannelService.Channel channel = new ChannelService.Channel();
                                    channel.GameID = message.message.GameID;
                                    channel.Members = message.message.ChannelMembers;
                                    Intent pressIntent = PressActivity.startPressIntent(NotificationReceiveActivity.this, gameSingleContainer.Properties, channel, member);
                                    if (android.os.Build.VERSION.SDK_INT > 15) {
                                        TaskStackBuilder.create(NotificationReceiveActivity.this)
                                                .addNextIntent(mainIntent)
                                                .addNextIntent(gameIntent)
                                                .addNextIntent(pressIntent).startActivities();
                                    } else {
                                        startActivity(pressIntent);
                                    }
                                }
                                finish();

                            }
                        }, getResources().getString(R.string.loading_state));
            } else if (message.type.equals("phase")) {
                startGameActivity(message.gameID);
            } else {
                App.firebaseCrashReport("Unknown message type " + message.type);
            }
        } else {
            App.firebaseCrashReport("Message without type: " + getIntent().getExtras());
        }
    }

    private void startGameActivity(String gameID) {
        handleReq(
                gameService.GameLoad(gameID),
                new Sendable<SingleContainer<Game>>() {
                    @Override
                    public void send(SingleContainer<Game> gameSingleContainer) {
                        Intent mainIntent = new Intent(NotificationReceiveActivity.this, MainActivity.class);
                        mainIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
                        Intent gameIntent;
                        if (gameSingleContainer.Properties.NewestPhaseMeta != null && gameSingleContainer.Properties.NewestPhaseMeta.size() > 0) {
                            gameIntent = GameActivity.startGameIntent(NotificationReceiveActivity.this, gameSingleContainer.Properties, gameSingleContainer.Properties.NewestPhaseMeta.get(0));
                        } else {
                            gameIntent = GameActivity.startGameIntent(NotificationReceiveActivity.this, gameSingleContainer.Properties, null);
                        }
                        if (android.os.Build.VERSION.SDK_INT > 15) {
                            TaskStackBuilder.create(NotificationReceiveActivity.this)
                                    .addNextIntent(mainIntent)
                                    .addNextIntent(gameIntent).startActivities();
                        } else {
                            startActivity(gameIntent);
                        }
                        finish();

                    }
                }, getResources().getString(R.string.loading_state));
    }
}
