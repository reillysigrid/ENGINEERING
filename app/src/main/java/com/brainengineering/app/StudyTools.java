package com.brainengineering.app;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.Locale;

/** Offline native study planner, timer and science quiz. No login or network. */
final class StudyTools {
    private static final String PREFS = "brainengineering_study_v1";
    private static final String TASKS = "tasks";
    private static final int MAX_TASKS = 30;
    private static final int NAVY = MainActivity.NAVY;
    private static final int TEAL = MainActivity.TEAL;
    private static final int MUTED = Color.rgb(79, 103, 108);
    private final Activity activity;
    private final SharedPreferences prefs;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable tick;
    private TextView timerText;
    private Button timerButton;
    private boolean timerRunning;
    private int secondsLeft = 25 * 60;
    private long finishAt;
    private LinearLayout tasksList;
    private TextView taskSummary;
    private TextView questionText, answerText, quizProgress;
    private LinearLayout answerButtons;
    private Button nextQuestion;
    private int questionIndex = 0;
    private int score = 0;
    private boolean answered = false;

    StudyTools(Activity activity) {
        this.activity = activity;
        this.prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
    private int dp(int n) { return (int)(n * activity.getResources().getDisplayMetrics().density + .5f); }
    private String text(int res) { return activity.getString(res); }
    private LinearLayout linear(boolean vertical) {
        LinearLayout l = new LinearLayout(activity);
        l.setOrientation(vertical ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        return l;
    }
    private GradientDrawable rounded(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color); drawable.setCornerRadius(dp(radius)); return drawable;
    }
    private TextView heading(String text, int size) {
        TextView t = new TextView(activity);
        t.setText(text); t.setTextColor(NAVY); t.setTextSize(size); t.setTypeface(null, Typeface.BOLD);
        return t;
    }
    private TextView body(String text) {
        TextView t = new TextView(activity); t.setText(text); t.setTextColor(MUTED); t.setTextSize(14);
        t.setLineSpacing(dp(2), 1f); return t;
    }
    private Button button(String label, int color) {
        Button b = new Button(activity);
        b.setText(label); b.setTextColor(Color.WHITE); b.setAllCaps(false);
        b.setTextSize(14); b.setMinHeight(0); b.setMinimumHeight(0);
        b.setBackground(rounded(color, 12));
        return b;
    }
    private LinearLayout card() {
        LinearLayout l = linear(true);
        l.setPadding(dp(16), dp(16), dp(16), dp(16));
        l.setBackground(rounded(Color.WHITE, 18));
        l.setElevation(dp(2));
        return l;
    }
    private void spacer(LinearLayout parent, int height) {
        View v = new View(activity);
        parent.addView(v, new LinearLayout.LayoutParams(1, dp(height)));
    }
    private LinearLayout.LayoutParams cardLayout() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.setMargins(dp(1), dp(8), dp(1), dp(8)); return p;
    }
    private ScrollView screen(LinearLayout content) {
        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(MainActivity.CREAM);
        scroll.setClipToPadding(false);
        content.setPadding(dp(17), dp(19), dp(17), dp(30));
        scroll.addView(content, new ScrollView.LayoutParams(-1,-2));
        return scroll;
    }
    private JSONArray getTasks() {
        try { return new JSONArray(prefs.getString(TASKS, "[]")); }
        catch (JSONException ignored) { return new JSONArray(); }
    }
    private void storeTasks(JSONArray items) {
        prefs.edit().putString(TASKS, items.toString()).apply();
        renderTasks();
    }

    View createPlanner() {
        LinearLayout content = linear(true);
        content.addView(heading(text(R.string.planner_title), 25));
        spacer(content, 7);
        content.addView(body(text(R.string.planner_subtitle)));
        spacer(content, 17);

        LinearLayout taskCard = card();
        taskCard.addView(heading(text(R.string.goals), 19));
        spacer(taskCard, 5);
        taskSummary = body("");
        taskCard.addView(taskSummary);
        spacer(taskCard, 12);
        EditText newGoal = new EditText(activity);
        newGoal.setSingleLine(true);
        newGoal.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        newGoal.setHint(R.string.goal_hint);
        newGoal.setTextSize(15);
        newGoal.setFilters(new InputFilter[]{new InputFilter.LengthFilter(120)});
        taskCard.addView(newGoal, new LinearLayout.LayoutParams(-1, dp(52)));
        spacer(taskCard, 7);
        Button add = button(text(R.string.add_goal), TEAL);
        taskCard.addView(add, new LinearLayout.LayoutParams(-1, dp(48)));
        add.setOnClickListener(v -> {
            String entry = newGoal.getText().toString().trim();
            if (entry.length() == 0) { newGoal.setError(text(R.string.goal_required)); return; }
            JSONArray tasks = getTasks();
            if (tasks.length() >= MAX_TASKS) {
                Toast.makeText(activity, R.string.goal_limit, Toast.LENGTH_SHORT).show(); return;
            }
            JSONObject item = new JSONObject();
            try { item.put("title", entry); item.put("done", false); tasks.put(item); }
            catch (JSONException ignored) { return; }
            storeTasks(tasks);
            newGoal.setText("");
        });
        spacer(taskCard, 8);
        tasksList = linear(true);
        taskCard.addView(tasksList);
        content.addView(taskCard, cardLayout());

        LinearLayout timerCard = card();
        timerCard.addView(heading(text(R.string.timer_title), 19));
        spacer(timerCard, 5);
        timerCard.addView(body(text(R.string.timer_description)));
        spacer(timerCard, 11);
        timerText = heading("25:00", 35); timerText.setGravity(Gravity.CENTER);
        timerCard.addView(timerText, new LinearLayout.LayoutParams(-1, dp(52)));
        spacer(timerCard, 9);
        LinearLayout actions = linear(false);
        timerButton = button(text(R.string.start), TEAL);
        Button resetButton = button(text(R.string.reset), NAVY);
        LinearLayout.LayoutParams half = new LinearLayout.LayoutParams(0, dp(48), 1f);
        half.setMargins(dp(2), 0, dp(2), 0);
        actions.addView(timerButton, half);
        LinearLayout.LayoutParams half2 = new LinearLayout.LayoutParams(0, dp(48), 1f);
        half2.setMargins(dp(2), 0, dp(2), 0);
        actions.addView(resetButton, half2);
        timerCard.addView(actions);
        timerButton.setOnClickListener(v -> toggleTimer());
        resetButton.setOnClickListener(v -> resetTimer());
        content.addView(timerCard, cardLayout());

        LinearLayout formulas = card();
        formulas.addView(heading(text(R.string.formulas_title), 19));
        spacer(formulas, 9);
        String[] lines = {
                "v = Δx / Δt", "F = m · a", "Eₖ = ½ m · v²", "n = m / M", "ρ = m / V"
        };
        for (String formula : lines) {
            TextView item = heading(formula, 17);
            item.setPadding(dp(5), dp(10), dp(5), dp(10));
            formulas.addView(item);
        }
        formulas.addView(body(text(R.string.formulas_note)));
        content.addView(formulas, cardLayout());
        spacer(content, 8);
        content.addView(body(text(R.string.local_only)));
        renderTasks();
        return screen(content);
    }
    private void renderTasks() {
        if (tasksList == null || taskSummary == null) return;
        tasksList.removeAllViews();
        JSONArray tasks = getTasks();
        int finished = 0;
        for (int i = 0; i < tasks.length(); i++) {
            JSONObject task = tasks.optJSONObject(i);
            if (task == null) continue;
            final int itemIndex = i;
            boolean done = task.optBoolean("done", false);
            if (done) finished++;
            LinearLayout row = linear(false);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(6), 0, dp(6));
            CheckBox check = new CheckBox(activity);
            check.setText(task.optString("title", ""));
            check.setTextColor(NAVY);
            check.setTextSize(15);
            check.setChecked(done);
            row.addView(check, new LinearLayout.LayoutParams(0,-2,1));
            check.setOnCheckedChangeListener((button, checked) -> {
                JSONArray updated = getTasks();
                JSONObject obj = updated.optJSONObject(itemIndex);
                if (obj == null) return;
                try { obj.put("done", checked); } catch (JSONException ignored) { return; }
                storeTasks(updated);
            });
            Button remove = button("×", Color.rgb(130, 69, 63));
            remove.setContentDescription(text(R.string.remove_goal));
            remove.setTextSize(19);
            row.addView(remove, new LinearLayout.LayoutParams(dp(44), dp(42)));
            remove.setOnClickListener(v -> {
                JSONArray updated = getTasks();
                JSONArray filtered = new JSONArray();
                for (int k=0;k<updated.length();k++) if (k!=itemIndex) filtered.put(updated.opt(k));
                storeTasks(filtered);
            });
            tasksList.addView(row);
        }
        taskSummary.setText(activity.getString(R.string.goal_count, finished, tasks.length()));
        if (tasks.length() == 0) tasksList.addView(body(text(R.string.empty_goals)));
    }
    private void toggleTimer() {
        if (timerRunning) {
            secondsLeft = Math.max(0, (int)Math.ceil((finishAt-SystemClock.elapsedRealtime()) / 1000.0));
            timerRunning = false;
            if (tick != null) handler.removeCallbacks(tick);
            timerButton.setText(R.string.start);
        } else {
            if (secondsLeft <= 0) secondsLeft = 25*60;
            finishAt = SystemClock.elapsedRealtime() + secondsLeft*1000L;
            timerRunning = true;
            timerButton.setText(R.string.pause);
            tick = new Runnable() {
                @Override public void run() {
                    if (!timerRunning) return;
                    secondsLeft = Math.max(0, (int)Math.ceil((finishAt-SystemClock.elapsedRealtime()) / 1000.0));
                    updateTimerText();
                    if (secondsLeft == 0) {
                        timerRunning = false;
                        timerButton.setText(R.string.start);
                        Toast.makeText(activity, R.string.timer_finished, Toast.LENGTH_LONG).show();
                    } else handler.postDelayed(this, 300);
                }
            };
            handler.post(tick);
        }
        updateTimerText();
    }
    private void resetTimer() {
        timerRunning = false;
        if (tick != null) handler.removeCallbacks(tick);
        secondsLeft = 25*60;
        timerButton.setText(R.string.start);
        updateTimerText();
    }
    private void updateTimerText() {
        if (timerText != null) timerText.setText(String.format(Locale.US, "%02d:%02d", secondsLeft/60, secondsLeft%60));
    }

    // Each tuple: question, A, B, C, correct choice (0..2), explanation.
    private static final String[][] ES = {
        {"¿Cuál es la unidad de fuerza en el SI?", "Julio", "Newton", "Watio", "1", "La fuerza se mide en newtons (N)."},
        {"Un coche recorre 120 km en 2 h. ¿Velocidad media?", "60 km/h", "240 km/h", "122 km/h", "0", "v = distancia / tiempo = 120/2 = 60 km/h."},
        {"¿Qué representa el número atómico Z?", "Neutrones", "Electrones más neutrones", "Protones", "2", "Z es el número de protones del núcleo."},
        {"¿Cuál es el pH de una disolución neutra a 25 °C?", "7", "0", "14", "0", "En agua pura a 25 °C, pH = 7."},
        {"Resuelve: 3x + 2 = 11", "x = 13", "x = 3", "x = 9", "1", "3x = 9; por tanto, x = 3."},
        {"En un sistema estacionario sin reacción ni pérdidas, ...", "Entrada = Salida", "Entrada = 0", "Salida > Entrada", "0", "Balance de materia: entrada menos salida = acumulación = 0."},
        {"¿Qué energía cinética tiene una masa de 2 kg a 3 m/s?", "6 J", "9 J", "18 J", "1", "E = ½mv² = ½·2·3² = 9 J."},
        {"¿Cuál es la unidad SI de cantidad de sustancia?", "Mol", "Gramo", "Litro", "0", "La unidad base es el mol (mol)."}
    };
    private static final String[][] EN = {
        {"What is the SI unit of force?", "Joule", "Newton", "Watt", "1", "Force is measured in newtons (N)."},
        {"A car travels 120 km in 2 h. Average speed?", "60 km/h", "240 km/h", "122 km/h", "0", "v = distance/time = 120/2 = 60 km/h."},
        {"What does atomic number Z count?", "Neutrons", "Electrons and neutrons", "Protons", "2", "Z is the number of protons in a nucleus."},
        {"What is neutral pH at 25 °C?", "7", "0", "14", "0", "Pure water at 25 °C has pH 7."},
        {"Solve: 3x + 2 = 11", "x = 13", "x = 3", "x = 9", "1", "3x = 9, so x = 3."},
        {"At steady state with no reaction or losses, ...", "Input = Output", "Input = 0", "Output > Input", "0", "Mass balance: input minus output = accumulation = 0."},
        {"Kinetic energy of 2 kg moving at 3 m/s?", "6 J", "9 J", "18 J", "1", "E = ½mv² = ½·2·3² = 9 J."},
        {"What is the SI unit for amount of substance?", "Mole", "Gram", "Litre", "0", "The base unit is the mole (mol)."}
    };
    private static final String[][] FR = {
        {"Quelle est l'unité SI de force ?", "Joule", "Newton", "Watt", "1", "La force se mesure en newtons (N)."},
        {"Une voiture parcourt 120 km en 2 h. Vitesse moyenne ?", "60 km/h", "240 km/h", "122 km/h", "0", "v = distance / temps = 120/2 = 60 km/h."},
        {"Que représente le numéro atomique Z ?", "Neutrons", "Électrons et neutrons", "Protons", "2", "Z est le nombre de protons dans le noyau."},
        {"Quel est le pH neutre à 25 °C ?", "7", "0", "14", "0", "Dans l'eau pure à 25 °C, pH = 7."},
        {"Résoudre : 3x + 2 = 11", "x = 13", "x = 3", "x = 9", "1", "3x = 9, donc x = 3."},
        {"En régime permanent sans réaction ni pertes, ...", "Entrée = Sortie", "Entrée = 0", "Sortie > Entrée", "0", "Bilan de matière : entrée - sortie = accumulation = 0."},
        {"Énergie cinétique de 2 kg à 3 m/s ?", "6 J", "9 J", "18 J", "1", "E = ½mv² = ½·2·3² = 9 J."},
        {"Quelle est l'unité SI de quantité de matière ?", "Mole", "Gramme", "Litre", "0", "L'unité de base est la mole (mol)."}
    };
    private String[][] questions() {
        String code = activity.getResources().getConfiguration().locale.getLanguage();
        if ("es".equals(code)) return ES;
        if ("fr".equals(code)) return FR;
        return EN;
    }
    View createPractice() {
        LinearLayout content = linear(true);
        content.addView(heading(text(R.string.practice_title), 25));
        spacer(content, 7);
        content.addView(body(text(R.string.practice_subtitle)));
        spacer(content, 17);
        LinearLayout block = card();
        quizProgress = body("");
        block.addView(quizProgress);
        spacer(block, 15);
        questionText = heading("", 19);
        block.addView(questionText);
        spacer(block, 14);
        answerButtons = linear(true);
        block.addView(answerButtons);
        spacer(block, 10);
        answerText = body("");
        block.addView(answerText);
        spacer(block, 11);
        nextQuestion = button(text(R.string.next), TEAL);
        block.addView(nextQuestion, new LinearLayout.LayoutParams(-1, dp(48)));
        nextQuestion.setOnClickListener(v -> {
            if (questionIndex >= questions().length - 1) {
                questionIndex = 0;
                score = 0;
            } else questionIndex++;
            renderQuestion();
        });
        content.addView(block, cardLayout());
        spacer(content, 10);
        content.addView(body(text(R.string.practice_private)));
        renderQuestion();
        return screen(content);
    }
    private void renderQuestion() {
        if (questionText == null || answerButtons == null) return;
        answered = false;
        String[][] qs = questions();
        String[] q = qs[questionIndex];
        quizProgress.setText(activity.getString(R.string.question_count, questionIndex + 1, qs.length, score));
        questionText.setText(q[0]);
        answerText.setText("");
        answerButtons.removeAllViews();
        nextQuestion.setVisibility(View.GONE);
        nextQuestion.setText(questionIndex == qs.length - 1 ? R.string.restart : R.string.next);
        for (int i = 0; i < 3; i++) {
            final int selected = i;
            Button answer = button(q[i+1], NAVY);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(56));
            p.setMargins(0, dp(5), 0, dp(5));
            answerButtons.addView(answer, p);
            answer.setOnClickListener(v -> {
                if (answered) return;
                answered = true;
                int correct = Integer.parseInt(q[4]);
                if (selected == correct) score++;
                answerText.setText((selected == correct ? text(R.string.correct) :
                        text(R.string.incorrect)) + " " + q[5]);
                quizProgress.setText(activity.getString(R.string.question_count, questionIndex+1, qs.length, score));
                for (int n = 0; n < answerButtons.getChildCount(); n++) {
                    Button option = (Button)answerButtons.getChildAt(n);
                    option.setEnabled(false);
                    option.setAlpha(n == correct ? 1f : .55f);
                }
                nextQuestion.setVisibility(View.VISIBLE);
                if (questionIndex == qs.length-1) {
                    answerText.append("\n" + activity.getString(R.string.final_score, score, qs.length));
                }
            });
        }
    }
    void shutdown() {
        timerRunning = false;
        if (tick != null) handler.removeCallbacks(tick);
        handler.removeCallbacksAndMessages(null);
    }
}
