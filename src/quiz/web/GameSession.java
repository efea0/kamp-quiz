package quiz.web;

import quiz.core.Quiz;
import quiz.model.Question;

class GameSession {



    record Feedback(boolean correct, boolean timedOut, int earnedPoints,
                    Question question, int chosenIndex) {
    }

    private final String playerName;
    private final Quiz quiz;
    private final String roomCode;


    private Feedback feedback;


    private Quiz.AnswerResult pendingAnswer;
    private boolean scoreSaved;

    GameSession(String playerName, Quiz quiz, String roomCode) {
        this.playerName = playerName;
        this.quiz = quiz;
        this.roomCode = roomCode;
    }

    String getPlayerName() {
        return playerName;
    }

    String getRoomCode() {
        return roomCode;
    }

    Quiz getQuiz() {
        return quiz;
    }

    Feedback getFeedback() {
        return feedback;
    }

    void setFeedback(Feedback feedback) {
        this.feedback = feedback;
    }

    void clearFeedback() {
        this.feedback = null;
    }

    synchronized Quiz.AnswerResult getPendingAnswer() {
        return pendingAnswer;
    }

    synchronized boolean hasPendingAnswer() {
        return pendingAnswer != null;
    }

    synchronized boolean setPendingAnswer(Quiz.AnswerResult answer) {
        if (pendingAnswer != null) {
            return false;
        }
        pendingAnswer = answer;
        return true;
    }

    synchronized Quiz.AnswerResult takePendingAnswer() {
        Quiz.AnswerResult answer = pendingAnswer;
        pendingAnswer = null;
        return answer;
    }

    boolean isScoreSaved() {
        return scoreSaved;
    }

    void markScoreSaved() {
        scoreSaved = true;
    }
}
