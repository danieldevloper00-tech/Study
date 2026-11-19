const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_baddie_1";
const BOB_UID = "bob_baddie_2";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user: denied across user profile and subcollections", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("tasks").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("tasks").doc("t1").set({
      userId: ALICE_UID,
      title: "Hack task",
      category: "STUDY",
      priority: "HIGH",
      status: "PENDING",
      completed: false,
      createdAt: new Date(Date.now() - 1000),
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});

test("Owner can create and update profile; ghost keys & cross-user access rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = new Date(Date.now() - 5000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Zarin",
      email: "zarin@example.com",
      timezone: "Asia/Dhaka",
      weekStartDay: "SATURDAY",
      themeMode: "DARK",
      accentPreset: "LAVENDER",
      compactLayout: false,
      aiMode: "ADVISOR",
      dailyStudyTargetMinutes: 180,
      onboardingComplete: false,
      widgetOrder: ["PROGRESS", "TUITION", "TASKS"],
      hiddenWidgets: [],
      telegramEnabled: false,
      telegramChatId: "",
      telegramPrefs: [],
      uptimeEnabled: false,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Partial update succeeds
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      onboardingComplete: true,
      aiMode: "AUTOPILOT",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Bob cannot read or write Alice's profile
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).update({ displayName: "Bob" }));

  // Ghost field rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isAdmin: true,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Blank list on users top-level denied
  await assertFails(aliceDb.collection("users").get());
});

test("Tasks: owner CRUD, @DocumentId id rejection, future timestamp rejection, cross-user denial", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = new Date(Date.now() - 5000);

  // Valid create (matches Kotlin Task payload without 'id' field)
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("tasks").doc("task_1").set({
      userId: ALICE_UID,
      title: "Finish Higher Math Trigonometry",
      description: "Exercise 7.1 problems 1-15",
      category: "HOMEWORK",
      subject: "Higher Mathematics",
      priority: "HIGH",
      status: "PENDING",
      completed: false,
      dueDate: "2026-11-19",
      startTime: "17:00",
      estimatedMinutes: 60,
      actualMinutes: 0,
      progressPercent: 0,
      recurrence: "NONE",
      tags: ["math", "tuition"],
      notes: "",
      reminderMinutesBefore: 15,
      goalId: "",
      sortOrder: 1,
      subtasks: [{ title: "Formulas", completed: true }],
      completedAtMillis: 0,
      createdAt: now,
      updatedAt: now,
    })
  );

  // List works even for owner
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("tasks").get());

  // Partial completion update
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("tasks").doc("task_1").update({
      completed: true,
      status: "COMPLETED",
      progressPercent: 100,
      completedAtMillis: Date.now(),
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject immutable createdAt modification
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("tasks").doc("task_1").update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject explicit 'id' field on create
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("tasks").doc("task_2").set({
      id: "task_2",
      userId: ALICE_UID,
      title: "Bad task",
      category: "STUDY",
      priority: "LOW",
      status: "PENDING",
      completed: false,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Reject future timestamp
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("tasks").doc("task_3").set({
      userId: ALICE_UID,
      title: "Future task",
      category: "STUDY",
      priority: "LOW",
      status: "PENDING",
      completed: false,
      createdAt: new Date(Date.now() + 60000),
      updatedAt: new Date(Date.now() + 60000),
    })
  );

  // Cross-user isolation
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("tasks").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("tasks").doc("task_1").delete());
});

test("Teachers, TuitionSessions, RoutineBlocks, Subjects, StudySessions, Habits, Goals, Journal, Sleep, Reminders, AI Logs", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = new Date(Date.now() - 5000);
  const userDoc = aliceDb.collection("users").doc(ALICE_UID);

  await assertSucceeds(
    userDoc.collection("teachers").doc("teach_1").set({
      userId: ALICE_UID,
      name: "Rahman Sir",
      subject: "Mathematics",
      defaultWeekdays: ["SUN", "TUE", "THU"],
      flexibleWeekly: false,
      targetSessionsPerWeek: 3,
      preferredTime: "16:00",
      expectedDurationMinutes: 90,
      contactInfo: "",
      notes: "",
      active: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    userDoc.collection("tuition_sessions").doc("ts_1").set({
      userId: ALICE_UID,
      teacherId: "teach_1",
      teacherName: "Rahman Sir",
      subject: "Mathematics",
      sessionDate: "2026-11-19",
      startTime: "16:00",
      durationMinutes: 90,
      status: "SCHEDULED",
      originalDate: "2026-11-19",
      originalTime: "16:00",
      rescheduledFromId: "",
      extraSession: false,
      notes: "",
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    userDoc.collection("routine_blocks").doc("rb_1").set({
      userId: ALICE_UID,
      title: "Morning Physics",
      blockType: "STUDY",
      startTime: "07:00",
      endTime: "08:30",
      durationMinutes: 90,
      repeatDays: ["SAT", "SUN", "MON", "TUE", "WED", "THU"],
      reminderEnabled: true,
      notes: "",
      sortOrder: 1,
      lastCompletedDate: "",
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    userDoc.collection("subjects").doc("sub_1").set({
      userId: ALICE_UID,
      name: "Physics",
      colorHex: "#C4B5FD",
      confidenceLevel: 4,
      totalStudyMinutes: 120,
      pendingTopics: "Vector Calculus",
      examPrepNotes: "",
      chapters: [{ title: "Vectors", status: "PRACTICING", progressPercent: 65, notes: "" }],
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    userDoc.collection("study_sessions").doc("ss_1").set({
      userId: ALICE_UID,
      subjectId: "sub_1",
      subjectName: "Physics",
      sessionDate: "2026-11-18",
      durationMinutes: 25,
      mode: "POMODORO",
      notes: "Focused sprint",
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    userDoc.collection("habits").doc("hab_1").set({
      userId: ALICE_UID,
      title: "Drink Water",
      frequency: "DAILY",
      targetDaysPerWeek: 7,
      reminderTime: "09:00",
      completedDates: ["2026-11-18"],
      notes: "",
      active: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    userDoc.collection("goals").doc("goal_1").set({
      userId: ALICE_UID,
      title: "Finish 3 Physics Chapters",
      description: "By Friday night",
      timeframe: "WEEKLY",
      priority: "HIGH",
      deadline: "2026-11-20",
      progressPercent: 33,
      autoCalculateFromTasks: true,
      completed: false,
      milestones: [{ title: "Chapter 1", completed: true }],
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    userDoc.collection("journal_entries").doc("j_1").set({
      userId: ALICE_UID,
      entryDate: "2026-11-18",
      mood: "Calm",
      energyLevel: 4,
      content: "Productive evening revision.",
      wins: "Solved 15 trig problems",
      difficulties: "A bit tired after 9 PM",
      aiSummary: "",
      aiSummaryEnabled: false,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    userDoc.collection("sleep_logs").doc("sl_1").set({
      userId: ALICE_UID,
      logDate: "2026-11-18",
      sleepTime: "23:30",
      wakeTime: "06:30",
      durationMinutes: 420,
      energyLevel: 4,
      mood: "Rested",
      exerciseMinutes: 20,
      notes: "",
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    userDoc.collection("reminders").doc("rem_1").set({
      userId: ALICE_UID,
      title: "Math Tuition",
      triggerDateTimeIso: "2026-11-19T15:45:00+06:00",
      timezone: "Asia/Dhaka",
      offsetMinutes: 15,
      repeatRule: "NONE",
      sourceType: "TUITION",
      sourceId: "ts_1",
      enabled: true,
      delivered: false,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    userDoc.collection("ai_action_logs").doc("ai_1").set({
      userId: ALICE_UID,
      mode: "ADVISOR",
      actionType: "RESCHEDULE_TUITION",
      summary: "Proposed moving Math tuition from Tue to Wed 4:00 PM",
      approved: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Bob cannot list any of Alice's subcollections
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("journal_entries").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("tuition_sessions").get());
});
