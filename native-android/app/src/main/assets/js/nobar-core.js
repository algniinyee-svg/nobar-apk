/* NOBAR portable business rules. UI and networking remain native Kotlin. */
var NobarCore = (function () {
  'use strict';
  var MAX_MEMBERS = 8;
  function cleanText(value, max) { return String(value == null ? '' : value).replace(/[<>]/g, '').trim().slice(0, max); }
  function normalizeRoom(value) { return String(value || '').toUpperCase().replace(/[^A-Z0-9]/g, '').slice(0, 12); }
  function validateVideo(video) {
    if (!video || typeof video !== 'object') return null;
    if (video.type === 'yt' && /^[\w-]{11}$/.test(video.id || '')) return { type: 'yt', id: video.id };
    if (video.type === 'url' && /^https?:\/\//i.test(video.url || '') && video.url.length < 1000) return { type: 'url', url: video.url };
    return null;
  }
  function canControl(memberCount, isHost) { return memberCount < 3 || !!isHost; }
  function canQueue(memberCount, isHost) { return memberCount < 3 || !!isHost; }
  function validateChat(value) { return cleanText(value, 300); }
  return { MAX_MEMBERS: MAX_MEMBERS, cleanText: cleanText, normalizeRoom: normalizeRoom,
    validateVideo: validateVideo, canControl: canControl, canQueue: canQueue, validateChat: validateChat };
}());
