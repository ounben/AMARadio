package net.ounben.AMARadio;

import net.ounben.AMARadio.service.PauseReason;
import net.ounben.AMARadio.station.DataRadioStation;
import net.ounben.AMARadio.station.live.StreamLiveInfo;
import net.ounben.AMARadio.station.live.ShoutcastInfo;
import net.ounben.AMARadio.players.PlayState;
import net.ounben.AMARadio.players.selector.PlayerType;
import android.support.v4.media.session.MediaSessionCompat;

interface IPlayerService
{
void SetStation(in DataRadioStation station);
void Play(boolean isAlarm);
void Pause(in PauseReason pauseReason);
void Resume();
void Stop();
void SkipToNext();
void SkipToPrevious();
void addTimer(int secondsAdd);
void clearTimer();
long getTimerSeconds();
String getCurrentStationID();
DataRadioStation getCurrentStation();
StreamLiveInfo getMetadataLive();
ShoutcastInfo getShoutcastInfo();
MediaSessionCompat.Token getMediaSessionToken();
boolean isPlaying();
PlayState getPlayerState();
long getTransferredBytes();
long getBufferedSeconds();
long getLastPlayStartTime();
boolean getIsHls();
PauseReason getPauseReason();
boolean isNotificationActive();

void enableMPD(String hostname, int port);
void disableMPD();

void warnAboutMeteredConnection(in PlayerType playerType);
}
