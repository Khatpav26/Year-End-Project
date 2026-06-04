import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.io.*;
import java.nio.file.*;

public class FlashcardApp {

    // --- Data ---
    static class Flashcard {
        String question;
        String answer;
        String deck;

        Flashcard(String deck, String question, String answer) {
            this.deck = deck;
            this.question = question;
            this.answer = answer;
        }
    }

    // --- Persistence ---
    private static final String SAVE_FILE = "flashiq_cards.txt";
    private static final String DELIMITER = "|||";

    private void saveCards() {
        try (PrintWriter pw = new PrintWriter(new FileWriter(SAVE_FILE))) {
            for (Flashcard c : allCards) {
                pw.println(c.deck + DELIMITER + c.question + DELIMITER + c.answer);
            }
        } catch (IOException e) {
            JOptionPane.showMessageDialog(frame, "Could not save cards: " + e.getMessage(), "Save Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void loadCards() {
        File f = new File(SAVE_FILE);
        if (!f.exists()) return;
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split("\\|\\|\\|", 3);
                if (parts.length == 3) {
                    allCards.add(new Flashcard(parts[0], parts[1], parts[2]));
                }
            }
        } catch (IOException e) {
            JOptionPane.showMessageDialog(null, "Could not load saved cards: " + e.getMessage(), "Load Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // --- State ---
    private JFrame frame;
    private CardLayout cardLayout;
    private JPanel mainPanel;

    private ArrayList<Flashcard> allCards = new ArrayList<>();
    private ArrayList<Flashcard> sessionCards = new ArrayList<>();
    private int currentIndex = 0;
    private boolean showingAnswer = false;
    private int correct = 0;
    private int incorrect = 0;

    // Quiz panel components
    private JLabel deckLabel;
    private JLabel cardCountLabel;
    private JTextArea cardTextArea;
    private JButton flipButton;
    private JButton correctButton;
    private JButton wrongButton;
    private JLabel progressLabel;
    private JPanel answerPanel;

    // Colors
    private static final Color BG_COLOR      = new Color(245, 247, 250);
    private static final Color CARD_COLOR    = new Color(255, 255, 255);
    private static final Color PRIMARY_COLOR = new Color(67, 97, 238);
    private static final Color GREEN_COLOR   = new Color(39, 174, 96);
    private static final Color RED_COLOR     = new Color(231, 76, 60);
    private static final Color GRAY_TEXT     = new Color(120, 130, 150);

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new FlashcardApp().launch());
    }

    void launch() {
        loadCards();

        frame = new JFrame("FlashIQ");
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                saveCards();
                System.exit(0);
            }
        });
        frame.setSize(600, 500);
        frame.setMinimumSize(new Dimension(500, 420));
        frame.setLocationRelativeTo(null);

        cardLayout = new CardLayout();
        mainPanel = new JPanel(cardLayout);
        mainPanel.setBackground(BG_COLOR);

        mainPanel.add(buildHomePanel(), "home");
        mainPanel.add(buildQuizPanel(), "quiz");
        mainPanel.add(buildResultPanel(), "result");
        mainPanel.add(buildManagePanel(), "manage");

        frame.add(mainPanel);
        frame.setVisible(true);
    }

    // ===================== HOME =====================
    private JPanel buildHomePanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 0));
        panel.setBackground(BG_COLOR);

        // Header
        JPanel header = new JPanel();
        header.setBackground(PRIMARY_COLOR);
        header.setBorder(new EmptyBorder(24, 30, 24, 30));
        JLabel title = new JLabel("FlashIQ");
        title.setFont(new Font("SansSerif", Font.BOLD, 26));
        title.setForeground(Color.WHITE);
        header.add(title);
        panel.add(header, BorderLayout.NORTH);

        // Center
        JPanel center = new JPanel();
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        center.setBackground(BG_COLOR);
        center.setBorder(new EmptyBorder(30, 50, 20, 50));

        JLabel subtitle = new JLabel("Choose a deck to study:");
        subtitle.setFont(new Font("SansSerif", Font.PLAIN, 15));
        subtitle.setForeground(GRAY_TEXT);
        subtitle.setAlignmentX(Component.CENTER_ALIGNMENT);
        center.add(subtitle);
        center.add(Box.createVerticalStrut(18));

        // Deck buttons
        Set<String> decks = new LinkedHashSet<>();
        decks.add("All Decks");
        for (Flashcard c : allCards) decks.add(c.deck);

        for (String deck : decks) {
            JButton btn = styledButton(deck, PRIMARY_COLOR, Color.WHITE);
            btn.setAlignmentX(Component.CENTER_ALIGNMENT);
            btn.setMaximumSize(new Dimension(300, 44));
            btn.addActionListener(e -> startQuiz(deck));
            center.add(btn);
            center.add(Box.createVerticalStrut(10));
        }

        center.add(Box.createVerticalStrut(10));
        JButton manageBtn = styledButton("Manage Cards", new Color(180, 190, 210), new Color(40, 50, 80));
        manageBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        manageBtn.setMaximumSize(new Dimension(300, 40));
        manageBtn.addActionListener(e -> { refreshManagePanel(); cardLayout.show(mainPanel, "manage"); });
        center.add(manageBtn);

        panel.add(center, BorderLayout.CENTER);

        // Footer
        JLabel footer = new JLabel("Total cards: " + allCards.size(), SwingConstants.CENTER);
        footer.setFont(new Font("SansSerif", Font.PLAIN, 12));
        footer.setForeground(GRAY_TEXT);
        footer.setBorder(new EmptyBorder(0, 0, 14, 0));
        panel.add(footer, BorderLayout.SOUTH);

        return panel;
    }

    // ===================== QUIZ =====================
    private JPanel buildQuizPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setBackground(BG_COLOR);
        panel.setBorder(new EmptyBorder(16, 24, 16, 24));

        // Top bar
        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setBackground(BG_COLOR);
        deckLabel = new JLabel("Deck");
        deckLabel.setFont(new Font("SansSerif", Font.BOLD, 15));
        deckLabel.setForeground(PRIMARY_COLOR);
        cardCountLabel = new JLabel("");
        cardCountLabel.setFont(new Font("SansSerif", Font.PLAIN, 13));
        cardCountLabel.setForeground(GRAY_TEXT);
        topBar.add(deckLabel, BorderLayout.WEST);
        topBar.add(cardCountLabel, BorderLayout.EAST);
        panel.add(topBar, BorderLayout.NORTH);

        // Card area
        JPanel cardWrapper = new JPanel(new BorderLayout());
        cardWrapper.setBackground(CARD_COLOR);
        cardWrapper.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(new Color(220, 225, 235), 1, true),
                new EmptyBorder(30, 30, 30, 30)
        ));

        JLabel qLabel = new JLabel("QUESTION", SwingConstants.CENTER);
        qLabel.setFont(new Font("SansSerif", Font.BOLD, 10));
        qLabel.setForeground(GRAY_TEXT);
        cardWrapper.add(qLabel, BorderLayout.NORTH);

        cardTextArea = new JTextArea();
        cardTextArea.setFont(new Font("SansSerif", Font.PLAIN, 20));
        cardTextArea.setLineWrap(true);
        cardTextArea.setWrapStyleWord(true);
        cardTextArea.setEditable(false);
        cardTextArea.setBackground(CARD_COLOR);
        cardTextArea.setForeground(new Color(30, 40, 60));
        cardTextArea.setAlignmentX(Component.CENTER_ALIGNMENT);
        JScrollPane scroll = new JScrollPane(cardTextArea);
        scroll.setBorder(null);
        scroll.setBackground(CARD_COLOR);
        cardWrapper.add(scroll, BorderLayout.CENTER);

        flipButton = styledButton("Show Answer", PRIMARY_COLOR, Color.WHITE);
        flipButton.setFont(new Font("SansSerif", Font.BOLD, 14));
        flipButton.addActionListener(e -> flipCard());
        cardWrapper.add(flipButton, BorderLayout.SOUTH);

        panel.add(cardWrapper, BorderLayout.CENTER);

        // Bottom: correct / wrong buttons + progress
        JPanel bottomPanel = new JPanel(new BorderLayout(0, 8));
        bottomPanel.setBackground(BG_COLOR);

        answerPanel = new JPanel(new GridLayout(1, 2, 12, 0));
        answerPanel.setBackground(BG_COLOR);
        answerPanel.setVisible(false);

        correctButton = styledButton("✓  Got it", GREEN_COLOR, Color.WHITE);
        correctButton.addActionListener(e -> markAnswer(true));
        wrongButton = styledButton("✗  Missed it", RED_COLOR, Color.WHITE);
        wrongButton.addActionListener(e -> markAnswer(false));
        answerPanel.add(correctButton);
        answerPanel.add(wrongButton);

        progressLabel = new JLabel("", SwingConstants.CENTER);
        progressLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        progressLabel.setForeground(GRAY_TEXT);

        JButton homeBtn = new JButton("← Back to Home");
        homeBtn.setFont(new Font("SansSerif", Font.PLAIN, 12));
        homeBtn.setBorderPainted(false);
        homeBtn.setContentAreaFilled(false);
        homeBtn.setForeground(GRAY_TEXT);
        homeBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        homeBtn.addActionListener(e -> cardLayout.show(mainPanel, "home"));

        bottomPanel.add(answerPanel, BorderLayout.CENTER);
        bottomPanel.add(progressLabel, BorderLayout.SOUTH);

        JPanel southMost = new JPanel(new BorderLayout());
        southMost.setBackground(BG_COLOR);
        southMost.add(bottomPanel, BorderLayout.CENTER);
        southMost.add(homeBtn, BorderLayout.SOUTH);
        panel.add(southMost, BorderLayout.SOUTH);

        return panel;
    }

    // ===================== RESULT =====================
    private JPanel resultPanel;
    private JLabel resultTitle, resultScore, resultMsg;

    private JPanel buildResultPanel() {
        resultPanel = new JPanel();
        resultPanel.setLayout(new BoxLayout(resultPanel, BoxLayout.Y_AXIS));
        resultPanel.setBackground(BG_COLOR);
        resultPanel.setBorder(new EmptyBorder(50, 60, 40, 60));

        resultTitle = new JLabel("Session Complete!", SwingConstants.CENTER);
        resultTitle.setFont(new Font("SansSerif", Font.BOLD, 24));
        resultTitle.setForeground(new Color(40, 50, 80));
        resultTitle.setAlignmentX(Component.CENTER_ALIGNMENT);

        resultScore = new JLabel("", SwingConstants.CENTER);
        resultScore.setFont(new Font("SansSerif", Font.BOLD, 42));
        resultScore.setForeground(PRIMARY_COLOR);
        resultScore.setAlignmentX(Component.CENTER_ALIGNMENT);

        resultMsg = new JLabel("", SwingConstants.CENTER);
        resultMsg.setFont(new Font("SansSerif", Font.PLAIN, 15));
        resultMsg.setForeground(GRAY_TEXT);
        resultMsg.setAlignmentX(Component.CENTER_ALIGNMENT);

        JButton homeBtn = styledButton("Back to Home", PRIMARY_COLOR, Color.WHITE);
        homeBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        homeBtn.setMaximumSize(new Dimension(240, 44));
        homeBtn.addActionListener(e -> cardLayout.show(mainPanel, "home"));

        resultPanel.add(resultTitle);
        resultPanel.add(Box.createVerticalStrut(20));
        resultPanel.add(resultScore);
        resultPanel.add(Box.createVerticalStrut(10));
        resultPanel.add(resultMsg);
        resultPanel.add(Box.createVerticalStrut(30));
        resultPanel.add(homeBtn);

        return resultPanel;
    }

    // ===================== MANAGE =====================
    private JPanel manageCardsList;
    private JTextField qField, aField, deckField;

    private JPanel buildManagePanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 0));
        panel.setBackground(BG_COLOR);

        // Header
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(PRIMARY_COLOR);
        header.setBorder(new EmptyBorder(14, 20, 14, 20));
        JLabel lbl = new JLabel("Manage Cards");
        lbl.setFont(new Font("SansSerif", Font.BOLD, 18));
        lbl.setForeground(Color.WHITE);
        JButton back = new JButton("← Back");
        back.setFont(new Font("SansSerif", Font.PLAIN, 13));
        back.setBorderPainted(false);
        back.setContentAreaFilled(false);
        back.setForeground(Color.WHITE);
        back.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        back.addActionListener(e -> cardLayout.show(mainPanel, "home"));
        header.add(lbl, BorderLayout.WEST);
        header.add(back, BorderLayout.EAST);
        panel.add(header, BorderLayout.NORTH);

        // Add card form
        JPanel addForm = new JPanel(new GridBagLayout());
        addForm.setBackground(BG_COLOR);
        addForm.setBorder(new EmptyBorder(14, 20, 10, 20));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        deckField = new JTextField("General");
        qField = new JTextField();
        aField = new JTextField();

        addFormRow(addForm, gbc, 0, "Deck:", deckField);
        addFormRow(addForm, gbc, 1, "Question:", qField);
        addFormRow(addForm, gbc, 2, "Answer:", aField);

        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 2;
        JButton addBtn = styledButton("Add Card", GREEN_COLOR, Color.WHITE);
        addBtn.addActionListener(e -> addCard());
        addForm.add(addBtn, gbc);

        panel.add(addForm, BorderLayout.NORTH);

        // Card list
        manageCardsList = new JPanel();
        manageCardsList.setLayout(new BoxLayout(manageCardsList, BoxLayout.Y_AXIS));
        manageCardsList.setBackground(BG_COLOR);
        JScrollPane listScroll = new JScrollPane(manageCardsList);
        listScroll.setBorder(new EmptyBorder(0, 0, 0, 0));
        listScroll.setBackground(BG_COLOR);
        panel.add(listScroll, BorderLayout.CENTER);

        return panel;
    }

    private void addFormRow(JPanel panel, GridBagConstraints gbc, int row, String label, JTextField field) {
        gbc.gridwidth = 1;
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        JLabel lbl = new JLabel(label);
        lbl.setFont(new Font("SansSerif", Font.PLAIN, 13));
        panel.add(lbl, gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        field.setFont(new Font("SansSerif", Font.PLAIN, 13));
        field.setPreferredSize(new Dimension(200, 28));
        panel.add(field, gbc);
    }

    // ===================== LOGIC =====================
    private void startQuiz(String deck) {
        sessionCards = new ArrayList<>();
        for (Flashcard c : allCards) {
            if (deck.equals("All Decks") || c.deck.equals(deck)) {
                sessionCards.add(c);
            }
        }
        if (sessionCards.isEmpty()) {
            JOptionPane.showMessageDialog(frame, "No cards in this deck.", "Empty Deck", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        Collections.shuffle(sessionCards);
        currentIndex = 0;
        correct = 0;
        incorrect = 0;
        showingAnswer = false;
        deckLabel.setText(deck.equals("All Decks") ? "All Decks" : deck);
        updateQuizCard();
        cardLayout.show(mainPanel, "quiz");
    }

    private void updateQuizCard() {
        if (currentIndex >= sessionCards.size()) {
            showResult();
            return;
        }
        Flashcard card = sessionCards.get(currentIndex);
        cardTextArea.setText(card.question);
        cardCountLabel.setText("Card " + (currentIndex + 1) + " of " + sessionCards.size());
        progressLabel.setText("✓ " + correct + "   ✗ " + incorrect);
        showingAnswer = false;
        flipButton.setText("Show Answer");
        flipButton.setVisible(true);
        answerPanel.setVisible(false);
    }

    private void flipCard() {
        if (!showingAnswer) {
            Flashcard card = sessionCards.get(currentIndex);
            cardTextArea.setText(card.answer);
            showingAnswer = true;
            flipButton.setVisible(false);
            answerPanel.setVisible(true);
        }
    }

    private void markAnswer(boolean wasCorrect) {
        if (wasCorrect) correct++; else incorrect++;
        currentIndex++;
        updateQuizCard();
    }

    private void showResult() {
        int total = correct + incorrect;
        int pct = total > 0 ? (correct * 100 / total) : 0;
        resultScore.setText(correct + " / " + total + " (" + pct + "%)");
        if (pct >= 90) resultMsg.setText("Excellent work! Keep it up.");
        else if (pct >= 70) resultMsg.setText("Good job! A bit more practice and you'll nail it.");
        else if (pct >= 50) resultMsg.setText("Not bad — review the ones you missed.");
        else resultMsg.setText("Keep studying — you'll get there!");
        cardLayout.show(mainPanel, "result");
    }

    private void addCard() {
        String deck = deckField.getText().trim();
        String q = qField.getText().trim();
        String a = aField.getText().trim();
        if (deck.isEmpty() || q.isEmpty() || a.isEmpty()) {
            JOptionPane.showMessageDialog(frame, "Please fill in all fields.", "Missing Info", JOptionPane.WARNING_MESSAGE);
            return;
        }
        allCards.add(new Flashcard(deck, q, a));
        saveCards();
        qField.setText("");
        aField.setText("");
        refreshManagePanel();
    }

    private void refreshManagePanel() {
        manageCardsList.removeAll();
        manageCardsList.setBorder(new EmptyBorder(0, 16, 10, 16));
        for (int i = 0; i < allCards.size(); i++) {
            final int idx = i;
            Flashcard c = allCards.get(i);
            JPanel row = new JPanel(new BorderLayout(10, 0));
            row.setBackground(i % 2 == 0 ? CARD_COLOR : new Color(238, 241, 248));
            row.setBorder(new EmptyBorder(8, 12, 8, 12));
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 60));

            JLabel info = new JLabel("<html><b>[" + c.deck + "]</b> " + c.question + "</html>");
            info.setFont(new Font("SansSerif", Font.PLAIN, 13));
            row.add(info, BorderLayout.CENTER);

            JButton del = new JButton("Remove");
            del.setFont(new Font("SansSerif", Font.PLAIN, 11));
            del.setForeground(RED_COLOR);
            del.setBorderPainted(false);
            del.setContentAreaFilled(false);
            del.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            del.addActionListener(e -> {
                allCards.remove(idx);
                saveCards();
                refreshManagePanel();
            });
            row.add(del, BorderLayout.EAST);

            manageCardsList.add(row);
        }
        if (allCards.isEmpty()) {
            JLabel empty = new JLabel("No cards yet. Add some above!", SwingConstants.CENTER);
            empty.setFont(new Font("SansSerif", Font.ITALIC, 14));
            empty.setForeground(GRAY_TEXT);
            manageCardsList.add(empty);
        }
        manageCardsList.revalidate();
        manageCardsList.repaint();
    }

    private JButton styledButton(String text, Color bg, Color fg) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("SansSerif", Font.BOLD, 13));
        btn.setBackground(bg);
        btn.setForeground(fg);
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setOpaque(true);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setBorder(new EmptyBorder(10, 20, 10, 20));
        return btn;
    }
}