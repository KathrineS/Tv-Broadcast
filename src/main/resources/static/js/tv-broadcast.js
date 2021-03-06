/*
 * (C) Copyright 2016 LiveU (http://liveu.tv/)
 *
 * TV Broadcast Application
 * 
 * Filename	: js/tv-broadcast.js
 * Purpose	: java script functionality for TV-Broadcast application
 * Author	: Sergey K, vburykh
 * Created	: 10/08/2016
 * Modified	: 03.12.2016
 */

var ws = null;
var participants = {};
var playUris = {};
var lives = {}; // live participants
var nextVideo = null;
var centralVideo = null;
var loggedInUser = null;
var loggedInRole = null;
var loggedInRoomType = null;
var loggedAfterAction = null;
var loggedAfterRoom = null;
var producerName = null;
var currentRoom = null;
var GeoX = 0;
var GeoY = 0;
var mapObject = null;
var participantsContainer = 'participants'; // contatiner for participants, must be set before news room or producer room enter
var producerRoomList = {};

/* Global array of rooms */
var roomList = [ {
	name : "World",
	descr : "world news",
	color : "Crimson",
	nusers : 0
} ];

/* room element id prefix */
const ROOM_ID_PREFIX = 'room_';
const PRODUCER_ROOM_ID_PREFIX = 'producer_room_';

/* page GET parameters */
var GET_PARAMS = null;

/* icons 4 marker */
var geoMarkers = {
	common : 'img/marker-dark-green2.png',
	selected : 'img/marker-red2.png',
	self : 'img/marker-bright-green2.png' };


/* cleaup websocekt before exit */
window.onbeforeunload = function() {
	ws.close();
};


function wsOnOpen() {
	console.info("WS connection on open");
	sendMessage({"id":"listRooms", "name":"ok"});
        sendMessage({"id":"recentUsers"});
	//sendMessage({"id":"addRoom", "name":"Computers", "descr":"computers,etc", "color":"DarkCyan"});

};

function wsOnClose() {
	console.info("WS connection on close");
};

function wsOnMessage(message) {
	console.info(message);
	var parsedMessage = JSON.parse(message.data);
	console.info('Received message: ' + message.data);

	switch (parsedMessage.id) {
	case 'error':
		onError(parsedMessage);
		break;
	case 'login':
		onLogin(parsedMessage);
		break;
	case 'listRooms':
		onListRooms(parsedMessage);
		break;          
        case 'listProducerRooms':
                onListProducerRooms(parsedMessage);
                break;
	case 'addRoom':
		onAddRoom(parsedMessage);
		break;
        case 'addProducerRoom':
            onAddProducerRoom(parsedMessage);
            break;
	case 'updateRoom':
		onUpdateRoom(parsedMessage);
		break;
	case 'updateProducerRoom':
		onUpdateProducerRoom(parsedMessage);
		break;
        case 'moveParticipant':
            onMoveParticipant(parsedMessage);
            break;
	case 'chat':
		onChat(parsedMessage);
		break;
	case 'geo':
		onGeo(parsedMessage);
		break;
	case 'existingParticipants':
		onExistingParticipants(parsedMessage);
		break;
        case 'recentUsers':
            onRecentUsers(parsedMessage);
            break;            
        case 'listPlayUri':
            onListPlayUri(parsedMessage);
            break;
	case 'makeLive':
		makeLive(parsedMessage);
		break;
	case 'newParticipantArrived':
		onNewParticipant(parsedMessage);
		break;
	case 'participantLeft':
		onParticipantLeft(parsedMessage);
		break;
	case 'receiveVideoAnswer':
		onReceiveVideoAnswer(parsedMessage);
		break;
	case 'sdpAnswer4NextVideo':
		onSdpAnswer4NextVideo(parsedMessage);
		break;
	case 'sdpAnswer4Central':
		centralVideo.rtcPeer.processAnswer (parsedMessage.sdpAnswer, function (error) {
			if (error) return console.error (error);
		});	
		break;
	case 'sdpAnswer4Live':
		onSdpAnswer4Live(parsedMessage);
		break;
        case 'sdpAnswer4PlayUri':
            onSdpAnswer4PlayUri(parsedMessage);
            break;            
	case 'selectedParticipant':
		chooseUser(parsedMessage.name);
		break;
        case 'publishCentral':
            changeCentralStatus(parsedMessage.name, true);
            break;
        case 'unpublishCentral':
            changeCentralStatus(parsedMessage.name, false);
            break;            
	case 'newPlayUri':
		onNewPlayUri(parsedMessage);
		break;
	case 'removePlayUri':
		onRemovePlayUri(parsedMessage);
		break;
	case 'iceCandidate':
		if (participants.hasOwnProperty(parsedMessage.name)) {
			// if we waiting this...
			// fixed bug when room leaved (participants destroyed) and slow message came
			participants[parsedMessage.name].rtcPeer.addIceCandidate(parsedMessage.candidate, function (error) {
				if (error) {
					console.error("Error adding candidate: " + error);
					return;
				}
			});
		}
	    break;
	case 'iceCandidate4NextVideo':
		if (nextVideo != null) {
			nextVideo.rtcPeer.addIceCandidate(parsedMessage.candidate, function (error) {
				if (error) {
					console.error("Error adding candidate 4 next video: " + error);
					return;
				}
			});
		}
		break;
	case 'iceCandidate4Central':
		if (centralVideo != null) {
			centralVideo.rtcPeer.addIceCandidate(parsedMessage.candidate, function (error) {
				if (error) {
					console.error("Error adding candidate 4 central video: " + error);
					return;
				}
			});
		}
		break;
	case 'iceCandidate4Live':
		if (lives.hasOwnProperty(parsedMessage.name)) {
			// if we waiting this...
			// fixed bug when room leaved (participants destroyed) and slow message came
			lives[parsedMessage.name].rtcPeer.addIceCandidate(parsedMessage.candidate, function (error) {
				if (error) {
					console.error("Error adding candidate 4 live: " + error);
					return;
				}
			});
		}
	    break;
	case 'iceCandidate4PlayUri':
            if (playUris.hasOwnProperty(parsedMessage.name)) {
                playUris[parsedMessage.name].rtcPeer.addIceCandidate(parsedMessage.candidate, function (error) {
                    if (error) {
                        console.error("Error adding candidate 4 play uri: " + error);
                        return;
                    }
                });
            }
            break;
	default:
		console.error('Unrecognized message', parsedMessage);
	}
}


function sendMessage(message) {
	var jsonMessage = JSON.stringify(message);
	console.log('Sending message: ' + jsonMessage);
	// states CONNECTING OPEN CLOSING CLOSED

	if (ws.readyState !== ws.OPEN){
		console.log("WS ERROR:" + ws.readyState + " NOT SENDING!");
		return;
	}
	ws.send(jsonMessage);
}


function register(n, r) {
	console.log("Registering " + n + " in " + r);
	document.getElementById('room-header').innerHTML = 'News room: ' + r;
//	document.getElementById('room').style.display = 'block';

	var message = {
		id : 'joinRoom',
		name : n,
		room : r,
		geox : GeoX,
		geoy : GeoY,
	}
        
        loggedInRoomType = "news";
        participantsContainer = "participants";
	sendMessage(message);
}


function registerProducerRoom(n, r, p) {
	console.log("Registering " + n + " in producer room" + r + " producer name " + p);
	document.getElementById('producer-room-header').innerHTML = 'Producer room: ' + r;
//	document.getElementById('room').style.display = 'block';

	var message = {
		id : 'joinProducerRoom',
		name : p,
		room : r,
		geox : GeoX,
		geoy : GeoY,
	}
        
        loggedInRoomType = "producer";
        participantsContainer = "producerParticipants";
	sendMessage(message);
}

function onMoveParticipant(r) {
    clickBackFromNewsRoom();
    page_1.style.display = "none";
    page_2.style.display = "none";
    page_3.style.display = "block";
    registerProducerRoom(r.user, r.room, r.producer);
    window.history.replaceState('test', '', "?producer_room=" + r.room + "&producer_name=" + r.producer);
}

function onChat(r) {
	addChatMessage(r);
}

function onGeo(r) {
	var p = participants[r.name];
	if (p != null) {
		if (r.name == loggedInUser) {
			participants[r.name].setGeoLocation(r.geox, r.geoy, 'self');			
		} else {
			participants[r.name].setGeoLocation(r.geox, r.geoy, 'common');
		}
		mapFitBounds();
	}
}

function onListRooms(r) {
	for (i = 0; i < r.data.length; i++)
		addRoom(r.data[i]);
}


function onListProducerRooms(r) {
    for (i = 0; i < r.data.length; i++)
        addProducerRoom(r.data[i]);
}


function onError(r) {
	console.log("Error: " + r.message);
	alert("Error: " + r.message)
}


function onAddRoom(r) {
	console.log("adding new room");
	addRoom({name : r.name, descr : r.descr, color : r.color, imageurl : r.imageurl, nusers : r.nusers});
}

function onAddProducerRoom(r) {
	console.log("adding new producer room");
	addProducerRoom({name : r.name, descr : r.descr, color : r.color, imageurl : r.imageurl, nusers : r.nusers});
}

function onUpdateRoom(r) {
	console.log("updating room: " + r.name);
	updateRoom({name : r.name, descr : r.descr, color : r.color, imageurl : r.imageurl, nusers : r.nusers});
}

function onUpdateProducerRoom(r) {
	console.log("updating producer room: " + r.name);
	updateProducerRoom({name : r.name, descr : r.descr, color : r.color, imageurl : r.imageurl, nusers : r.nusers});
}

function onLogin(r) {
	console.log("login done message");
	afterLoginAttemp(r);
}

/*
 * new participant arrive
 */
function onNewParticipant(r) {
	var participant = new Participant(r.name);
	participants[r.name] = participant;
	receiveVideo(participant);
	participant.setGeoLocation(r.geox, r.geoy, 'common');
	mapFitBounds();
}


/*
 * new play uri came
 */
function onNewPlayUri(r) {
	var playUri = new PlayUri(r.name, r.title);
	playUris[r.name] = playUri;
        receivePlayUriVideo(playUri);
}


function onReceiveVideoAnswer(result) {
	participants[result.name].rtcPeer.processAnswer (result.sdpAnswer, function (error) {
		if (error) return console.error (error);
	});
}

/*
 * process SDP answer 4 next video
 */
function onSdpAnswer4NextVideo(r) {
	nextVideo.rtcPeer.processAnswer (r.sdpAnswer, function (error) {
		if (error) return console.error (error);
	});	
}


/*
 * process SDP answer 4 live
 */
function onSdpAnswer4Live(result) {
	lives[result.name].rtcPeer.processAnswer (result.sdpAnswer, function (error) {
		if (error) return console.error (error);
	});
}


/*
 * process SDP answer 4 play uri
 */
function onSdpAnswer4PlayUri(result) {
	playUris[result.name].rtcPeer.processAnswer (result.sdpAnswer, function (error) {
		if (error) return console.error (error);
	});
}

//
//function callResponse(message) {
//	if (message.response != 'accepted') {
//		console.info('Call not accepted by peer. Closing call');
//		stop();
//	} else {
//		webRtcPeer.processAnswer(message.sdpAnswer, function (error) {
//			if (error) return console.error (error);
//		});
//	}
//}

/*
 * get list of existing participants, create myself participant
 */
function onExistingParticipants(msg) {
    var constraints = null;
    
    if (loggedInRole === "producer") {
        // disable video
        constraints = {
            audio : true,
            video : false            
        };
    } else {
        constraints = {
            audio : true,
            video : {
		mandatory : {
                    maxWidth : 300,
                    maxFrameRate : 15,
                    minFrameRate : 15
		}
            }
	};
    }
	
	console.log(loggedInUser + " registered in room " + msg.room);
        currentRoom = msg.room;

	//create self participant
	var participant = new Participant(loggedInUser);

	// send geo coordinates
	sendMessage({
		id : 'geo',
		geox : GeoX,
		geoy : GeoY
	});

	/* set geo for myself */	
//	if (GeoX != null && GeoY != null) {
//		participant.setGeoLocation(GeoX, GeoY, 'self');
//	}
	
	participants[loggedInUser] = participant;
	var video = participant.getVideoElement();

	var options = {
	      localVideo: video,
	      mediaConstraints: constraints,
	      onicecandidate: participant.onIceCandidate.bind(participant)
	    }
	participant.rtcPeer = new kurentoUtils.WebRtcPeer.WebRtcPeerSendonly(options,
		function (error) {
		  if(error) {
			  return console.error(error);
		  }
		  this.generateOffer (participant.offerToReceiveVideo.bind(participant));
	});

	// create other participants
	msg.data.forEach(function(item) {
		var participant = new Participant(item.name);
		participants[item.name] = participant;
		receiveVideo(participant);
		participant.setGeoLocation(item.geox, item.geoy, 'common');
                participant.setCentralStatus(item.is_live);
	});
	
	// create next video
	nextVideo = new NextVideo();

	if (msg.hasOwnProperty('selected')) {
		chooseUser(msg.selected);
	}

	receiveNextVideo(nextVideo);
	
	mapFitBounds();
	setChat(msg.chat);
			
}

function leaveRoom() {
	sendMessage({
		id : 'leaveRoom'
	});

	for (var key in participants)
		participants[key].dispose();
	
	participants = {};
	
	if (nextVideo != null) {
		nextVideo.dispose();
		nextVideo = null;
	}

	//document.getElementById('room').style.display = 'none';
	document.getElementById('room-header').innerHTML = 'no active room!';
        currentRoom = null;
}


function leaveProducerRoom() {
	sendMessage({
		id : 'leaveProducerRoom'
	});

	for (var key in participants)
		participants[key].dispose();
	
	participants = {};
	
	for (var key in playUris)
		playUris[key].dispose();
	
	playUris = {};

    if (nextVideo != null) {
		nextVideo.dispose();
		nextVideo = null;
	}

	//document.getElementById('room').style.display = 'none';
	document.getElementById('producer-room-header').innerHTML = 'no active room!';
        currentRoom = null;
}

function receiveVideo(participant) {
	var video = participant.getVideoElement();

	var options = {
      remoteVideo: video,
      onicecandidate: participant.onIceCandidate.bind(participant)
    }

	participant.rtcPeer = new kurentoUtils.WebRtcPeer.WebRtcPeerRecvonly(options,
			function (error) {
			  if(error) {
				  return console.error(error);
			  }
			  this.generateOffer (participant.offerToReceiveVideo.bind(participant));
	});
}

function receiveNextVideo(nv) {
	var video = nv.getVideoElement();

	var options = {
      remoteVideo: video,
      onicecandidate: nv.onIceCandidate.bind(nv)
    }

	nv.rtcPeer = new kurentoUtils.WebRtcPeer.WebRtcPeerRecvonly(options,
			function (error) {
			  if(error) {
				  return console.error(error);
			  }
			  this.generateOffer (nv.offerToReceiveVideo.bind(nv));
	});
}


/*
 * get list of existing play uris
 */
function onListPlayUri(msg) {
	msg.data.forEach(function(item) {
            var playUri = new PlayUri(item.name, item.title);
            playUris[item.name] = playUri;
            receivePlayUriVideo(playUri);
	});	
}


function receivePlayUriVideo(playUri) {
    var video = playUri.getVideoElement();

    var options = {
        remoteVideo: video,
        onicecandidate: playUri.onIceCandidate.bind(playUri)
    }

    playUri.rtcPeer = new kurentoUtils.WebRtcPeer.WebRtcPeerRecvonly(options,
            function (error) {
                if (error) {
                    return console.error(error);
                }
                this.generateOffer(playUri.offerToReceiveVideo.bind(playUri));
            });
}


function onRemovePlayUri(request) {
	console.log('Play Uri ' + request.name + ' left');
	var playUri = playUris[request.name];
	playUri.dispose();
	delete playUris[request.name];
}


function onParticipantLeft(request) {
	console.log('Participant ' + request.name + ' left');
	var participant = participants[request.name];
	participant.dispose();
	delete participants[request.name];
}

/*
 * get list of live participants, create myself live
 */
function makeLive(msg) {
	//alert("MAKE LIVE");
	var constraints = {
		audio : true,
		video : {
			mandatory : {
				maxWidth : 300,
				maxFrameRate : 15,
				minFrameRate : 15
			}
		}
	};

	//create self live
	var live = new LiveVideo(loggedInUser);
	
	lives[loggedInUser] = live;
	var video = live.getVideoElement();

	var options = {
	      localVideo: video,
	      mediaConstraints: constraints,
	      onicecandidate: live.onIceCandidate.bind(live)
	    };
	
	live.rtcPeer = new kurentoUtils.WebRtcPeer.WebRtcPeerSendonly(options,
		function (error) {
		  if(error) {
			  return console.error(error);
		  }
		  this.generateOffer (live.offerToReceiveVideo.bind(live));
	});

	// create other lives
	msg.data.forEach(function(item) {
		var live = new LiveVideo(item.name);
		lives[item.name] = live;
		receiveLiveVideo(live);
	});
	
	activateLivePage();
}


function receiveLiveVideo(live) {
	var video = live.getVideoElement();

	var options = {
      remoteVideo: video,
      onicecandidate: live.onIceCandidate.bind(live)
    };

	live.rtcPeer = new kurentoUtils.WebRtcPeer.WebRtcPeerRecvonly(options,
			function (error) {
			  if(error) {
				  return console.error(error);
			  }
			  this.generateOffer (live.offerToReceiveVideo.bind(live));
	});
}




/*
 * PARTICIPANT SECTION
 */
//const PARTICIPANTS_CONTAINER = 'participants'; // overiden by participantsContainer
const PARTICIPANT_MAIN_CLASS = 'user main';
const PARTICIPANT_CLASS = 'user';
const PARTICIPANT_SELECTED_CLASS = 'user selected';
const PARTICIPANT_ME_CLASS = 'user me';
const PARTICIPANT_ME_SELECTED_CLASS = 'user me selected';


/**
 * Creates a video element for a new participant
 *
 * @param {String} name - the name of the new participant, to be used as tag
 *                        name of the video element.
 *                        The tag of the new element will be 'video<name>'
 * @return
 */
function Participant(name) {
	console.log("creating participant:'" + name + "'");

	this.name = name;
	this.geox = null;
	this.geoy = null;
	this.marker = null;
	this.selected = false;
        this.isCentral = false;

	this.container = document.createElement('div');
	this.container.className = PARTICIPANT_CLASS;
	if (name == loggedInUser) {
		this.container.className = PARTICIPANT_ME_CLASS;
	}
	this.container.id = name;
	var span = document.createElement('span');
	var video = document.createElement('video');
	var rtcPeer;

        var that = this;
        
	var btnSelect = document.createElement('input');
	btnSelect.value = "Select";
	btnSelect.type = "button";
	btnSelect.id = "video_" + name + "_button";
	btnSelect.className = "user-video-button";
	btnSelect.onclick = function() {
		that.requestSelection();
	};
        // this funtionality is disabled
        btnSelect.style.display = 'none';
        
        var btnMove = document.createElement('input');
        btnMove.value = "Move";
        btnMove.type = "button";
        btnMove.className = "user-move-button";
        btnMove.onclick = function() {
		that.requestMove();
        };
	
        var btnLive = document.createElement('input');
        btnLive.value = "Live!";
        btnLive.type = "button";
        btnLive.className = "user-live-button";
        btnLive.onclick = function() {
		that.requestLive();
        };
	
        var btnStopLive = document.createElement('input');
        btnStopLive.value = "Stop!";
        btnStopLive.type = "button";
        btnStopLive.className = "user-live-button";
        btnStopLive.onclick = function() {
		that.stopLive();
        };
	
	this.container.appendChild(video);
	this.container.appendChild(span);
	this.container.appendChild(btnSelect);
        this.container.appendChild(btnMove);
        this.container.appendChild(btnLive);
        this.container.appendChild(btnStopLive);

	this.container.onmouseover = function() {
            if (loggedInRoomType === "news" && producerName !== null) {
                // we can move somebody if we are producer and in news room
                btnMove.style.display = "block";
            }

            if (loggedInRoomType === "producer" && producerName !== null) {
                // we can Live! somebody if we are producer and in producer room
                if (that.isCentral === true) {
                    btnLive.style.display = "none";
                    btnStopLive.style.display = "block";
                } else {
                    btnLive.style.display = "block";    
                    btnStopLive.style.display = "none";
                }
            }
	}

	this.container.onmouseout = function() {
            btnSelect.style.display = 'none';
            btnMove.style.display = "none";
            btnLive.style.display = "none";
            btnStopLive.style.display = "none";
	}	
	
//container.onclick = switchContainerClass;

	//container.onclick = function() {
	//	onChooseUser(name, container);
	//};
	
	document.getElementById(participantsContainer).appendChild(this.container);
	
	span.appendChild(document.createTextNode(name));

	video.id = 'video-' + name;
	video.autoplay = true;
	video.controls = true;

	this.getElement = function() {
		return this.container;
	};

	this.getVideoElement = function() {
		return video;
	};
	
	this.setGeoLocation = function(geox, geoy, markertype) {
		console.log("setLocation() user: " + this.name + " x: " + geox + " y: " + geoy + " marker: " + markertype);
		this.clearGeoLocation();
		this.geox = geox;
		this.geoy = geoy;
		this.marker = mapAddMarker(this.name, this.geox, this.geoy, markertype);
		if (this.marker !== null) {
			var that = this;
			this.marker.addListener('click',  function () {
				that.requestSelection();
			});
		}
	};

	this.clearGeoLocation = function() {
		if (this.marker != null) {
			this.marker.setMap(null);
			this.marker = null;
			this.geox = null;
			this.geoy = null;
		}
	};
	
	this.requestMove = function() {
		console.log("requesting user move: " + this.name);
                showMoveUser2ProducerRoom(this.name);	
	};
	
	this.requestLive = function() {
		console.log("requesting start Live!: " + this.name);
                var msg = { id: 'publishCentral', name: this.name };
                sendMessage(msg);
	};
        
        this.stopLive = function() {
            console.log("stopping Live!: " + this.name);
            var msg = { id: 'unpublishCentral', name: this.name};
            sendMessage(msg);
        };
	
	this.requestSelection = function() {
		console.log("requesting user selection: " + this.name);
		var msg = {
				id: 'selectParticipant',
				name: this.name
		};
		sendMessage(msg);		
	};
	
        this.setCentralStatus = function(isStreaming) {
            if (isStreaming === true) {
                console.log("setting Central streaming status");
                this.container.className = PARTICIPANT_SELECTED_CLASS;              
                this.isCentral = true;
            } else {
                console.log("unsetting Cetnral streaming status");
                this.container.className = PARTICIPANT_CLASS;
                this.isCentral = false;
            }
        };
        
	this.setSelected = function(state) {
		if (state === true) {
			if (this.selected === true) return;

			this.setGeoLocation(this.geox, this.geoy, "selected");
			if (this.name == loggedInUser) {
				this.container.className = PARTICIPANT_ME_SELECTED_CLASS;				
			} else {
				this.container.className = PARTICIPANT_SELECTED_CLASS;
			}
			this.selected = true;
			return;
		}
		
		// else is false
		if (this.selected === false) return;
		if (this.name == loggedInUser) {
			this.setGeoLocation(this.geox, this.geoy, "self");
			this.container.className = PARTICIPANT_ME_CLASS;			
		} else {
			this.setGeoLocation(this.geox, this.geoy, "common");
			this.container.className = PARTICIPANT_CLASS;
		}
		this.selected = false;		
	};
	
	this.offerToReceiveVideo = function(error, offerSdp, wp){
		if (error) return console.error ("sdp offer error");
		console.log('Invoking SDP offer callback function');
		var msg =  { id : "receiveVideoFrom",
				sender : name,
				sdpOffer : offerSdp
			};
		sendMessage(msg);
	};

	this.onIceCandidate = function (candidate, wp) {
		  console.log("Local candidate" + JSON.stringify(candidate));

		  var message = {
		    id: 'onIceCandidate',
		    candidate: candidate,
		    name: this.name
		  };
		  sendMessage(message);
	};

	Object.defineProperty(this, 'rtcPeer', { writable: true});

	this.dispose = function() {
		console.log('Disposing participant ' + this.name);
		this.rtcPeer.dispose();
		this.clearGeoLocation();
		mapFitBounds();
		this.container.parentNode.removeChild(this.container);
		console.log('Disposing participant ' + this.name + ' DONE');
	};
}


/*
 * PLAY URI SECTION
 */
//const PLAYURI_CONTAINER = 'participants'; // overiden by participantsContainer
const PLAYURI_CLASS = 'user playuri';


/**
 * Creates a video element for a new Play URI
 *
 * @param {String} name - the id of PlayerEndpoint, to be used as tag name of the video element.
 *                        The tag of the new element will be 'playuri-<id>'
 * @param {String} title - title as seen in browser
 * @return
 */
function PlayUri(name, title) {
	console.log("creating play uri:'" + name + "' title:'" + title + "'");

	this.name = name;
        this.title = title;

	this.container = document.createElement('div');
	this.container.className = PLAYURI_CLASS;
	this.container.id = name;
	var span = document.createElement('span');
	var video = document.createElement('video');
	var rtcPeer;

        var that = this;
        
	var btnRemove = document.createElement('input');
	btnRemove.value = "Remove";
	btnRemove.type = "button";
	btnRemove.id = "btnPlayUriRemove_" + this.name;
	btnRemove.className = "user-video-button";
	btnRemove.onclick = function() {
		that.requestRemove();
	};
        // this funtionality is disabled
        btnRemove.style.display = 'none';
        
	this.container.appendChild(video);
	this.container.appendChild(span);
	this.container.appendChild(btnRemove);

	this.container.onmouseover = function() {
                    //if (loggedInRoomType === "producer" && producerName !== null) {
                // we can move somebody if we are producer
                btnRemove.style.display = "block";
           // }
	};

	this.container.onmouseout = function() {
            btnRemove.style.display = 'none';
	};	
	
	document.getElementById(participantsContainer).appendChild(this.container);
	
	span.appendChild(document.createTextNode(this.title));

	video.id = 'videoPlayUri_' + this.name;
	video.autoplay = true;
	video.controls = true;

	this.getElement = function() {
		return this.container;
	};

	this.getVideoElement = function() {
		return video;
	};
	
	
	this.requestRemove = function() {
		console.log("requesting play uri remove: " + this.name);
                var msg = { id: 'removePlayUri', name: this.name };
                sendMessage(msg);
	};
	
	
	this.offerToReceiveVideo = function(error, offerSdp, wp){
		if (error) return console.error ("sdp offer error");
		console.log('Invoking play uri SDP offer callback function');
		var msg =  { id : "sdpOffer4PlayUri",
				sender : this.name,
				sdpOffer : offerSdp
			};
		sendMessage(msg);
	};

	this.onIceCandidate = function (candidate, wp) {
		  console.log("Local candidate" + JSON.stringify(candidate));

		  var message = {
		    id: 'onIceCandidate4PlayUri',
		    candidate: candidate,
		    name: this.name
		  };
		  sendMessage(message);
	}

	Object.defineProperty(this, 'rtcPeer', { writable: true});

	this.dispose = function() {
		console.log('Disposing play uri ' + this.name);
		this.rtcPeer.dispose();
		this.container.parentNode.removeChild(this.container);
		console.log('Disposing play uri ' + this.name + ' DONE');
	};
}


/*
 * NEXT VIDEO SECTION
 */
const NEXT_VIDEO_CLASS = 'user-video-next';
const NEXT_VIDEO_ID = 'video_next_id';
const NEXT_VIDEO_CONTAINER = 'video_next';

/**
 * Creates a video element for a new participant
 */
function NextVideo() {
	console.log("creating next video");

	this.username = 'not selected!';
	
	this.container = document.createElement('div');
	this.container.className = NEXT_VIDEO_CLASS;
	this.container.id = NEXT_VIDEO_ID;
	this.span = document.createElement('span');
	var video = document.createElement('video');
	var rtcPeer;

	this.container.appendChild(video);
	this.container.appendChild(this.span);
	
	document.getElementById(NEXT_VIDEO_CONTAINER).appendChild(this.container);
	
	this.span.appendChild(document.createTextNode(this.username));

	video.id = NEXT_VIDEO_ID + '-video';
	video.autoplay = true;
	video.controls = true;

	this.getElement = function() {
		return this.container;
	}

	this.getVideoElement = function() {
		return video;
	}
	
	this.setUserName = function(name) {
		this.username = name;
		old = this.span;
		this.span = document.createElement('span');
		this.span.appendChild(document.createTextNode(this.username));		
		old.parentNode.replaceChild(this.span, old);
	}
	
	this.offerToReceiveVideo = function(error, offerSdp, wp){
		if (error) return console.error ("sdp offer error");
		console.log('Invoking SDP offer callback function');
		var msg =  { id : "sdpOffer4NextVideo",
				sdpOffer : offerSdp
			};
		sendMessage(msg);
	}

	this.onIceCandidate = function (candidate, wp) {
		  console.log("Local candidate" + JSON.stringify(candidate));

		  var message = {
		    id: 'onIceCandidate4NextVideo',
		    candidate: candidate,
		  };
		  sendMessage(message);
	}

	Object.defineProperty(this, 'rtcPeer', { writable: true});

	this.dispose = function() {
		console.log('Disposing next video');
		this.rtcPeer.dispose();
		this.container.parentNode.removeChild(this.container);
		console.log('Disposing next video');
	};
}


/*
 * LIVE VIDEO SECTION
 */
const LIVE_VIDEO_CLASS = 'live';
const LIVE_VIDEO_ME_CLASS = 'live me';
const LIVE_VIDEO_ID = 'live_id';
const LIVE_VIDEO_CONTAINER = 'lives';

/**
 * Creates a video element for a live EP
 */
function LiveVideo(name) {
	console.log("creating live:'" + name + "'");

	this.name = name;

	this.container = document.createElement('div');
	this.container.className = LIVE_VIDEO_CLASS;
	if (name == loggedInUser) {
		this.container.className = LIVE_VIDEO_ME_CLASS;
	}
	this.container.id = LIVE_VIDEO_ID;
	this.span = document.createElement('span');
	var video = document.createElement('video');
	var rtcPeer;

	this.container.appendChild(video);
	this.container.appendChild(this.span);
	
	document.getElementById(LIVE_VIDEO_CONTAINER).appendChild(this.container);
	
	this.span.appendChild(document.createTextNode(this.name));

	video.id = LIVE_VIDEO_ID + this.name + '-video';
	video.autoplay = true;
	video.controls = true;

	this.getElement = function() {
		return this.container;
	}

	this.getVideoElement = function() {
		return video;
	}
	
	this.offerToReceiveVideo = function(error, offerSdp, wp){
		if (error) return console.error ("sdp offer error");
		console.log('Invoking SDP offer callback function');
		var msg =  { id : "sdpOffer4Live",
				sender : this.name,
				sdpOffer : offerSdp
			};
		sendMessage(msg);
	}

	this.onIceCandidate = function (candidate, wp) {
		  console.log("Local candidate" + JSON.stringify(candidate));

		  var message = {
		    id: 'onIceCandidate4Live',
		    name: this.name,
		    candidate: candidate,
		  };
		  sendMessage(message);
	}

	Object.defineProperty(this, 'rtcPeer', { writable: true});

	this.dispose = function() {
		console.log('Disposing live video');
		this.rtcPeer.dispose();
		this.container.parentNode.removeChild(this.container);
		console.log('Disposing live video');
	};
}


/*
 * CENTRAL VIDEO SECTION
 */
const CENTRAL_VIDEO_CLASS = 'user';
const CENTRAL_VIDEO_ID = 'central_id';
const CENTRAL_VIDEO_CONTAINER = 'central';

/**
 * Creates a video element for central
 */
function CentralVideo() {
	console.log("creating central video");

	this.username = 'Central';
	
	this.container = document.createElement('div');
	this.container.className = CENTRAL_VIDEO_CLASS;
	this.container.id = CENTRAL_VIDEO_ID;
	this.span = document.createElement('span');
	var video = document.createElement('video');
	var rtcPeer;

	this.container.appendChild(video);
	this.container.appendChild(this.span);
	
	document.getElementById(CENTRAL_VIDEO_CONTAINER).appendChild(this.container);
	
	this.span.appendChild(document.createTextNode(this.username));

	video.id = CENTRAL_VIDEO_ID + '-video';
	video.autoplay = true;
	video.controls = true;

	this.getElement = function() {
		return this.container;
	}

	this.getVideoElement = function() {
		return video;
	}
	
	this.setUserName = function(name) {
		this.username = name;
		old = this.span;
		this.span = document.createElement('span');
		this.span.appendChild(document.createTextNode(this.username));		
		old.parentNode.replaceChild(this.span, old);
	}
	
	this.offerToReceiveVideo = function(error, offerSdp, wp){
		if (error) return console.error ("sdp offer error");
		console.log('Invoking SDP offer callback function');
		var msg =  { id : "sdpOffer4Central",
				sdpOffer : offerSdp
			};
		sendMessage(msg);
	}

	this.onIceCandidate = function (candidate, wp) {
		  console.log("Local candidate" + JSON.stringify(candidate));

		  var message = {
		    id: 'onIceCandidate4Central',
		    candidate: candidate,
		  };
		  sendMessage(message);
	}

	Object.defineProperty(this, 'rtcPeer', { writable: true});

	this.dispose = function() {
		console.log('Disposing central video');
		this.rtcPeer.dispose();
		this.container.parentNode.removeChild(this.container);
		console.log('Disposing central video');
	};
}


/*
 * GUI SECTION
 */


/*
 * init websocket server connection
 * uses global ws
 */
function initWS() {
	ws = new WebSocket('wss://' + location.host + '/groupcall');
	ws.onopen = wsOnOpen;
	ws.onclose = wsOnClose;
	ws.onmessage = wsOnMessage;
}

/*
 * Page startup
 */
function initPage() {
	window.onerror = function(message, url, lineNumber) {
		alert("Uncatched error\n" +
		      "Message: " + message + "\n(" + url + ":" + lineNumber + ")");
	};

	btnCreateRoom.style.visibility = 'hidden';

	// make websocket connection
	initWS();

	GET_PARAMS = getSearchParameters();
	if (GET_PARAMS['room'] != null) {
            loggedAfterAction = "ROOM";
            loggedAfterRoom = GET_PARAMS['room'];
            showLoginPanel();
            return;
        }

        if (GET_PARAMS['producer_room'] != null && GET_PARAMS['producer_name'] != null) {
            loggedAfterAction = "PRODUCER_ROOM";
            loggedAfterRoom = GET_PARAMS['producer_room'];
            linkProducerName = GET_PARAMS['producer_name'];
            showLoginPanel();
            return;            
        }
        
    page_0.style.display = "none";
    page_1.style.display = "block";
    page_2.style.display = "none";
    page_3.style.display = "none";
    
	//roomList.forEach(function(item) {
	//	addRoom(item)
	//});
}

/*
 * get page params
 */
function getSearchParameters() {
	var prmstr = window.location.search.substr(1);
	return prmstr != null && prmstr != "" ? transformToAssocArray(prmstr) : {};
}

function transformToAssocArray(prmstr) {
	var params = {};
	var prmarr = prmstr.split("&");
	for (var i = 0; i < prmarr.length; i++) {
		var tmparr = prmarr[i].split("=");
		params[tmparr[0]] = tmparr[1];
	}
	return params;
}

function findRoomByName(roomName) {
	for (i = 0; i < roomList.length; i++) {
		if (roomName === roomList[i].name)
			return roomList[i];
	}
}

function findRoomById(roomId) {
	if (roomId.indexOf(ROOM_ID_PREFIX) != 0)
		return null;
	return findRoomByName(roomId.substr(ROOM_ID_PREFIX.length));
}

function addRoom(item) {
	var container = document.getElementById('roomList');
	container.appendChild(createRoomBox(item));
}

function addProducerRoom(item) {
    var container = document.getElementById('producerRoomList');
    producerRoomList[item.name] = item.descr;
    container.appendChild(createProducerRoomBox(item));
}

function updateRoom(item) {
	var container = document.getElementById('roomList');
	var elem = document.getElementById(ROOM_ID_PREFIX + item.name);
	newElem = createRoomBox(item);
	container.replaceChild(newElem, elem);
}

function updateProducerRoom(item) {
	var container = document.getElementById('producerRoomList');
	var elem = document.getElementById(PRODUCER_ROOM_ID_PREFIX + item.name);
	newElem = createProducerRoomBox(item);
        try {
            container.replaceChild(newElem, elem);
        } catch (err) {
            console.log("[E] updateProducerRoom: " + err);
        }
}

function createRoomBox(item) {
	var elem = document.createElement('div');
	elem.id = ROOM_ID_PREFIX + item.name;
	elem.className = "static-room static-room-active";
	elem.style.background = item.color;

	//var content = document.createElement('div');
	//content.className = "static-room-content";
	elem.innerHTML = "<b>" + item.name + "</b><br>" + item.descr;
	var nh = document.createElement('h3');
	nh.innerHTML = item.nusers;
//			+ "<br> Users: " + item.nusers + "<br>";
	var btn = document.createElement('input');
	btn.value = "Enter";
	btn.type = "button";
	btn.id = ROOM_ID_PREFIX + item.name + "_button";
	btn.className = "button-room";
	btn.onclick = function() {
		clickRoom(item.name);
	};
	
	elem.onmouseover = function() {
		loggedInUser != null ? btn.style.display = "block"
			: btn.style.display = "none";
	}

	elem.onmouseout = function() {
	    btn.style.display = 'none';
	}
	
	
	// var butcontent = document.createElement('div');
	// butcontent.style.textAlign = "center";
	// butcontent.appendChild(btn);
	// content.appendChild(butcontent);

	elem.appendChild(btn);
	elem.appendChild(nh);
//	elem.appendChild(content);
	return elem;
}

/* click on room panel */
function clickRoom(room) {
	page_1.style.display = "none";
	page_2.style.display = "block";
	page_3.style.display = "none";

        register(loggedInUser, room);
	window.history.replaceState('test', '', "?room=" + room);
	// TODO
	mapInit();
}

function createProducerRoomBox(item) {
	var elem = document.createElement('div');
	elem.id = PRODUCER_ROOM_ID_PREFIX + item.name;
	elem.className = "static-room static-room-active";
	elem.style.background = item.color;

	//var content = document.createElement('div');
	//content.className = "static-room-content";
	elem.innerHTML = "<b>" + item.name + "</b><br>" + item.descr;
	var nh = document.createElement('h3');
	nh.innerHTML = item.nusers;
//			+ "<br> Users: " + item.nusers + "<br>";
	var btn = document.createElement('input');
	btn.value = "Enter";
	btn.type = "button";
	btn.id = PRODUCER_ROOM_ID_PREFIX + item.name + "_button";
	btn.className = "button-room";
	btn.onclick = function() {
		clickProducerRoom(item.name);
	};
	
	elem.onmouseover = function() {
		loggedInUser != null ? btn.style.display = "block"
			: btn.style.display = "none";
	}

	elem.onmouseout = function() {
	    btn.style.display = 'none';
	}
	
	
	// var butcontent = document.createElement('div');
	// butcontent.style.textAlign = "center";
	// butcontent.appendChild(btn);
	// content.appendChild(butcontent);

	elem.appendChild(btn);
	elem.appendChild(nh);
//	elem.appendChild(content);
	return elem;
}

/* click on producer room panel */
function clickProducerRoom(room) {
	page_1.style.display = "none";
	page_2.style.display = "none";
	page_3.style.display = "block";
	// room_header_2.innerHTML = item.name;
	registerProducerRoom(loggedInUser, room, producerName);
	window.history.replaceState('test', '', "?producer_room=" + room + "&producer_name=" + producerName);
        mapInit();
}

/* login panel */
function showLoginPanel() {
	loginPanel.style.visibility = 'visible';
	loginPanel.style.left = ((document.body.clientWidth - 200) >> 1) + 'px';
	loginPanel.style.top = '200px';
	glassPanel.style.visibility = 'visible';
}

function clickLoginPanel() {
	// btnCreateRoom.disabled = false;
        
	sendMessage({
		id : 'login',
		login : login.value,
		password : null,
                role : role.value
	// login.password ?
	});
}

/*
 * called by incoming login event
 */
function afterLoginAttemp(item) {
	if (item.result != 'OK') {
		alert("Failed to login as: " + login.value + ", reason: "
				+ item.message);
                loginPanel.style.visibility = 'hidden';
                glassPanel.style.visibility = 'hidden';
                page_0.style.display = "none";
                page_1.style.display = "block";
                page_2.style.display = "none";
                page_3.style.display = "none";

                return;
	}

        loggedInUser = item.login.replace(/['"]+/g, '');
	loggedInRole = item.role.replace(/['"]+/g, '');
	statusLogin.innerHTML = "Login: " + loggedInUser + "<br>Role:  " + loggedInRole;
	btnCreateRoom.style.visibility = 'visible';
	btnCreateRoom.disabled = false;

	loginPanel.style.visibility = 'hidden';
	glassPanel.style.visibility = 'hidden';

	/* get user location */
	if (navigator.geolocation) {
		navigator.geolocation.getCurrentPosition(saveGeoPosition);
	} else {
		console.log("unable get location!");
	}
        
        if (loggedInRole === "producer") {
            producerRoomPane.style.display = 'block';
            producerName = loggedInUser;
            sendMessage({"id":"listProducerRooms", "name":"ok"});
        }

        if (loggedAfterAction === "ROOM") {
            /* redirect to news room */
            clickRoom(loggedAfterRoom);
            page_0.style.display = "none";
        }

        if (loggedAfterAction === "PRODUCER_ROOM") {
            /* redirect to producer room */
            page_1.style.display = "none";
            page_2.style.display = "none";
            page_3.style.display = "block";
            registerProducerRoom(loggedInUser, loggedAfterRoom, linkProducerName);
            mapInit();
            page_0.style.display = "none";
        }
}


/*
 * callback 4 geolocation
 */
function saveGeoPosition(position) {
	GeoY = position.coords.latitude;
	GeoX = position.coords.longitude;
	console.log("saveGeoPosition: (" + GeoX + "," + GeoY + ")");
	sendMessage({
		id : 'geo',
		geox : GeoX,
		geoy : GeoY
	});
}


/*
 * init Google map
 */
function mapInit() {
	console.log("mapInit()");
    //var coord = {lat: GeoY, lng: GeoX};
	mapObject = new google.maps.Map(document.getElementById('panel_map'), {
      zoom: 4,
      center: {lat: 40, lng: 30}
    });
}


/*
 * set Google map bounds from participants
 */
function mapFitBounds() {
	// bounds
	var b = {
	    north: 0,
	    south: 0,
	    east: 0,
	    west: 0
	};
	
	// is bound came?
	var c = {
		    north: false,
		    south: false,
		    east: false,
		    west: false			
	};
	
	// get bounds if any
	for (key in participants) {
		x = participants[key].geox;
		y = participants[key].geoy;
		
		if (x == 0 || y == 0) continue;
		
		// north
		if (c.north == false) {
			c.north = true; b.north = y;
		} else {
			b.north = Math.max(b.north,y);
		}
		
		// south
		if (c.south == false) {
			c.south = true; b.south = y;
		} else {
			b.south = Math.min(b.south,y);
		}

		// east
		if (c.east == false) {
			c.east = true; b.east = x;
		} else {
			b.east = Math.max(b.east,x);
		}
		
		// west
		if (c.west == false) {
			c.west = true; b.west = x;
		} else {
			b.west = Math.min(b.west,x);
		}		
	}
	
	if (b.east == b.west && b.north == b.south) {
		// only one point
		mapObject.setZoom(5);
		mapObject.setCenter(new google.maps.LatLng(b.south, b.east));
		return;
	} 

	if (c.east && c.west && c.north && c.south) {
		mapObject.fitBounds(b);
		//mapObject.panToBounds(b);
	}
}


/*
 * add market to Google map @mapObject
 */
function mapAddMarker(name, geox, geoy, markertype) {
	console.log("mapAddMarker: " + name + " (" + geox + "," + geoy + ")");
	console.log("geoMarker:" + geoMarkers['common']);
	if (geox == 0 || geoy == 0) {
		console.log("mapAddMarker: null coordinates!");
		return null;
	}
	
	var coord = {lat: geoy, lng: geox};
	
    return new google.maps.Marker({
      position: coord,
      map: mapObject,
      icon: geoMarkers[markertype],
      title: name,
      label: name[0].toUpperCase()
    });
}

function hideLoginPanel() {
	loginPanel.style.visibility = 'hidden';
	glassPanel.style.visibility = 'hidden';
        page_0.style.display = "none";
        page_1.style.display = "block";
        page_2.style.display = "none";
        page_3.style.display = "none";
}

/* create new room */
function showCreateRoomPanel() {
    if (loggedInRole === "producer") {
        /* producer */
	create_room_producer.style.visibility = 'visible';
	create_room_producer.style.left = ((document.body.clientWidth - 200) >> 1) + 'px';
	create_room_producer.style.top = '200px';
	glassPanel.style.visibility = 'visible';        
    } else {
        /* participant */
	create_room.style.visibility = 'visible';
	create_room.style.left = ((document.body.clientWidth - 200) >> 1) + 'px';
	create_room.style.top = '200px';
	glassPanel.style.visibility = 'visible';
    }
}

/* create news room */
function clickCreateRoom() {
	create_room.style.visibility = 'hidden';
	glassPanel.style.visibility = 'hidden';

	sendMessage({
		id : 'addRoom',
		name : newRoomName.value,
		descr : newRoomDescr.value,
                imageurl : newRoomImageURL.value,
		color : newRoomColor.value
	});
}

function hideCreateRoom() {
	create_room.style.visibility = 'hidden';
	glassPanel.style.visibility = 'hidden';
}

/* create producer or news room */
function clickCreateProducerRoom() {
	create_room_producer.style.visibility = 'hidden';
	glassPanel.style.visibility = 'hidden';

        if (newRoomProducerType.value === "producer") {
            /* producer room */
            sendMessage ({
		id : 'addProducerRoom',
		name : newRoomProducerName.value,
		descr : newRoomProducerDescr.value,
                imageurl : newRoomProducerImageURL.value,
		color : newRoomProducerColor.value
            });
        } else {
            /* news room */
            sendMessage ({
		id : 'addRoom',
		name : newRoomProducerName.value,
		descr : newRoomProducerDescr.value,
                imageurl : newRoomProducerImageURL.value,
		color : newRoomProducerColor.value
            });
        }
}

function hideCreateProducerRoom () {
	create_room_producer.style.visibility = 'hidden';
	glassPanel.style.visibility = 'hidden';
}

function showMoveUser2ProducerRoom (username) {
    /* fill select element with id "producerRoomList" */
    producerRoomListSelect.options.length = 0;
    for (key in producerRoomList) {
        var o = document.createElement("option");
        o.value = key;
        o.innerHTML = key + " (" + producerRoomList[key] + ")";
        producerRoomListSelect.appendChild(o);
    }
    
    document.getElementById("moveUserName").value = username;
    document.getElementById("moveProducerUser").innerHTML = "Move participant <b>'" + username + "'</b> to Producer Room";
    moveUserPanel.style.visibility = 'visible';
    moveUserPanel.style.left = ((document.body.clientWidth - 200) >> 1) + 'px';
    moveUserPanel.style.top = '200px';
    glassPanel.style.visibility = 'visible';
}

function clickMoveUser2ProducerRoom () {
    var r = producerRoomListSelect.options[producerRoomListSelect.selectedIndex].value;
    var n = document.getElementById("moveUserName").value;
    
    sendMessage ({
	id : 'moveParticipant',
        room : r,
        user : n,
        producer : producerName
    });
    
    hideMoveUser2ProducerRoom();
}


function hideMoveUser2ProducerRoom () {
    moveUserPanel.style.visibility = 'hidden';
    glassPanel.style.visibility = 'hidden';
}
/*
 * back 2 room list
 */
function clickBackFromNewsRoom() {
    leaveRoom();
    page_1.style.display = "block";
    page_2.style.display = "none";
    window.history.replaceState('test', '', '?');	
}

/*
 * back 2 room list
 */
function clickBackFromProducerRoom() {
    leaveProducerRoom();
    page_1.style.display = "block";
    page_3.style.display = "none";
    window.history.replaceState('test', '', '?');	
}

/*
 * mail url link to producer room
 */
function clickMailProducerRoom() {
    var subject = "Invitation%20to%20room%20%27" + currentRoom + "%27%20from%20%27" + loggedInUser + "%27";
    var body = "Please%2C%20join%20us%20at%20" + encodeURIComponent(window.location.href) + "%0D%0A%0D%0ABest%20regards%2C%0D%0A" + loggedInUser;
    var msg = "mailto:?subject=" + subject + "&body=" + body;
    //var w = window.open(msg, '_self');
    var w = window.open(msg);
    w.close();
}


/*
 * mute all participants
 */
var roomMuted = false;
function clickMuteRoom() {
    roomMuted = !roomMuted;
    if (roomMuted) {
        document.getElementById("btnMuteRoom").value = "Unmute Room";
        document.getElementById("btnMuteProducerRoom").value = "Unmute Room";
    } else {
        document.getElementById("btnMuteRoom").value = "Mute Room";
        document.getElementById("btnMuteProducerRoom").value = "Mute Room";        
    }
    
    for (var key in participants)
	participants[key].getVideoElement().muted = roomMuted;
    
    for (var key in playUris)
        playUris[key].getVideoElement().muted = roomMuted;
}


/*
 * show add stream panel 
 */
function showAddStreamPanel() {
    addStreamPanel.style.visibility = 'visible';
    addStreamPanel.style.left = ((document.body.clientWidth - 200) >> 1) + 'px';
    addStreamPanel.style.top = '200px';
    glassPanel.style.visibility = 'visible';
}


/*
 * hide show add stream panel
 */
function hideAddStreamPanel() {
    addStreamPanel.style.visibility = 'hidden';
    glassPanel.style.visibility = 'hidden';
}

/*
 * add stream to participants container
 */
function clickAddStream() {
    hideAddStreamPanel();
    
    sendMessage({
        id : 'addPlayUri',
        uri : addStreamUri.value,
        title : addStreamTitle.value
    });
}


/*
 * send message 2 chat
 */
function clickSendChatMessage() {
	var msg = document.getElementById('chat_input').value;
	var chat = document.getElementById('chat_box');
	//chat.innerHTML += msg + "<br>";
	sendMessage({
		id : 'chat',
		message : msg
	});
	document.getElementById('chat_input').value = '';
	//chat.scrollTop = chat.scrollHeight;
}


/**
 * format chat message
 */
function formatChatMessage(r) {
	return "<b>" + r.date + "&nbsp;<font color=Blue>" + r.name + "</font></b>&nbsp;" + r.message + "<br>";
}

/**
 * add message 2 chat
 */
function addChatMessage(r) {
	var chat = document.getElementById('chat_box');
	chat.innerHTML += formatChatMessage(r);
	chat.scrollTop = chat.scrollHeight;
}


/**
 * set chat last messages
 * @param chat
 */
function setChat(data) {
	var chat = document.getElementById('chat_box');
	var cont = "";
	for (i = 0; i < data.length; i++) {
		cont += formatChatMessage(data[i]);
	}
	chat.innerHTML = cont;
	chat.scrollTop = chat.scrollHeight;
}

/**
 * OLD click on participant video
 */
function OLDonChooseUser(name, elem) {
	console.log("onChooseUser(): " + name);
	
	// clear selection
	for (key in participants) {
		if (participants[key].selected === true) {
			participants[key].setSelected(false);
		}		
	}
	// set user with name selected
	participants[name].setSelected(true);
	
	var old = document.getElementById('video_next');
	var n = document.createElement('div');
	n.id = "video_next";
	n.className = "user-video-next";
	
	// dublicate video
	var v = document.getElementById('video-' + name);
	var video = v.cloneNode(true);
	var span = document.createElement('span');
	span.appendChild(document.createTextNode(name));
	n.appendChild(video);
	n.appendChild(span);
	
	n.onclick = function() {
		var v = document.getElementById('video_next');
		var vtr = v.cloneNode(true);
		var ctr = document.getElementById('video_translation');		
		ctr.parentNode.replaceChild(vtr, ctr);
		vtr.id = "video_translation";
		page_2.style.display = "none";
		page_4.style.display = "block";
//		v.pause();
//		v.muted = true;
	};
	old.parentNode.replaceChild(n, old);
//	elem.pause();
//	elem.muted = true;

} 


/**
 * click on participant video
 */
function chooseUser(name) {
	console.log("chooseUser(): " + name);
	
	// clear selection
	for (key in participants) {
		if (participants[key].selected === true) {
			participants[key].setSelected(false);
		}		
	}
	// set user with name selected
	participants[name].setSelected(true);

	nextVideo.setUserName(name);
} 


/**
 * changed status of participant
 * @param {string} name
 * @param {boolean} isStreaming
 * @returns {undefined}
 */
function changeCentralStatus(name, isStreaming) {
    console.log("changing Central status for: " + name + " to: " + isStreaming);
    participants[name].setCentralStatus(isStreaming);
}


/*
 * start live!
 */
function clickStartLive() {
	sendMessage({ id : 'startLive' });
}


/*
 * make live
 */
function activateLivePage() {
	page_2.style.display = "none";
	page_4.style.display = "block";
}


/*
 * stop live
 */
function backRoomPage() {
	sendMessage({ id: 'stopLive' });
	for (var key in lives)
		lives[key].dispose();
	
	lives = {};
	sendMessage({ id : 'disconnectCentral' });
	page_4.style.display = "none";
	page_2.style.display = "block";
}


/*
 * connect 2 central
 */
function connectCentral() {
	console.log("trying connect to Central");
	sendMessage({ id : 'connectCentral' });
	return;
//	
//	centralVideo = new CentralVideo();
//
//	var video = centralVideo.getVideoElement();
//
//	var options = {
//	     remoteVideo: video,
//	     onicecandidate: centralVideo.onIceCandidate.bind(centralVideo)
//	}
//
//	centralVideo.rtcPeer = new kurentoUtils.WebRtcPeer.WebRtcPeerRecvonly(options,
//		function (error) {
//		  if(error) {
//			  return console.error(error);
//		  }
//		  this.generateOffer (centralVideo.offerToReceiveVideo.bind(centralVideo));
//	});
}


function onRecentUsers(msg) {
    console.log("applying recent users");

    old = document.getElementById('recentUsersPane');
    container = document.createElement('div');
    
    msg.data.forEach(function(item) {
        elem = document.createElement('div');
        txt = document.createTextNode(item.name);
        elem.appendChild(txt);
        container.appendChild(elem);
    });

    old.parentNode.replaceChild(container, old);
    container.id = 'recentUsersPane';
}
