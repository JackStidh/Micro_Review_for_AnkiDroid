package com.orlikalabs.microreview;

public class StoredState {
    long deckId;
    int cardOrd;
    long noteID;
    String question = "";
    String answer = "";
    int buttonCount = 4;
    String deckName = "";

    /** When the card comes back after each answer, as AnkiDroid words it ("<10m", "4d"): a JSON array, or empty. */
    String nextReviewTimes = "";

    /** False for the "empty" state stored when there is no card to review. */
    boolean hasCard() {
        return !(noteID == -1 && cardOrd == -1);
    }

    /** The card to show again in the notification, without querying AnkiDroid. */
    CardInfo toCardInfo() {
        CardInfo card = new CardInfo();
        card.noteID = noteID;
        card.cardOrd = cardOrd;
        card.deckId = deckId;
        card.deckName = deckName;
        card.q = question;
        card.a = answer;
        card.buttonCount = buttonCount;
        if (nextReviewTimes != null && !nextReviewTimes.isEmpty()) {
            try {
                card.nextReviewTexts = new org.json.JSONArray(nextReviewTimes);
            } catch (org.json.JSONException e) {
                card.nextReviewTexts = null;
            }
        }
        return card;
    }
}
