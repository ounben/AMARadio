package com.ounben.amaradio;

import com.ounben.amaradio.service.PauseReason;
import com.ounben.amaradio.station.DataRadioStation;
import com.ounben.amaradio.station.live.StreamLiveInfo;
import com.ounben.amaradio.station.live.ShoutcastInfo;
import com.ounben.amaradio.players.PlayState;
import com.ounben.amaradio.players.selector.PlayerType;
import android.support.v4.media.session.MediaSessionCompat;

interface IPlayerService
{
void SetStation(in DataRadioStation station);
void Play();
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

void warnAboutMeteredConnection(in PlayerType playerType);
}
