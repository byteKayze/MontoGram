/* Montogram modifications, GPL-2.0-or-later. */
package org.telegram.messenger;

/** On-device format identifiers. Existing literals are immutable. No account content. */
enum MontogramLocalFeature {
    QUICK_REPLIES(1,"montogram-retained-quick-replies-v1","montogram.quick-replies.v1.",MontogramQuickReplyData.MAX_BYTES),
    BOOKMARKS(2,"montogram-retained-bookmarks-v1","montogram.bookmarks.v1.",MontogramBookmarkData.MAX_BYTES),
    NOTES(3,"montogram-retained-notes-v1","montogram.notes.v1.",MontogramNoteData.MAX_BYTES);
    final int kind,maxBytes;
    final String directory,keyPrefix;
    MontogramLocalFeature(int kind,String directory,String keyPrefix,int maxBytes){this.kind=kind;this.directory=directory;this.keyPrefix=keyPrefix;this.maxBytes=maxBytes;}
    String keyAlias(int account,long self){return keyPrefix+account+"."+self;}
}
