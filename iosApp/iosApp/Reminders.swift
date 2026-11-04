import Foundation
import UserNotifications
import Shared

/// Recordatorio diario opcional (VISION fase 3). iOS no deja calcular el texto al sonar, así que se programan los
/// próximos 7 días con lo que toca cada uno y se rehacen al abrir la app o tras cada cambio.
enum Reminders {
    static func requestAccess() async throws -> Bool {
        try await UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound])
    }

    static func reschedule(_ core: Shared.BrivelApp) {
        let center = UNUserNotificationCenter.current()
        let calendar = Calendar.current
        let now = Date()
        // Solo los del recordatorio: el de fin de descanso (RestAlert) se queda
        center.removePendingNotificationRequests(withIdentifiers: (0...7).compactMap { offset in
            calendar.date(byAdding: .day, value: offset, to: now).map { day in
                let p = calendar.dateComponents([.year, .month, .day], from: day)
                return String(format: "reminder-%04d-%02d-%02d", p.year!, p.month!, p.day!)
            }
        })
        guard let hour = core.data.reminderHour?.intValue else { return }
        for offset in 0..<7 {
            guard let day = calendar.date(byAdding: .day, value: offset, to: now),
                  let fire = calendar.date(bySettingHour: hour, minute: 0, second: 0, of: day), fire > now else { continue }
            let parts = calendar.dateComponents([.year, .month, .day], from: day)
            let date = String(format: "%04d-%02d-%02d", parts.year!, parts.month!, parts.day!)
            guard let text = try? core.reminderText(date: date) else { continue }  // descanso o ya hecho: no avisa
            let content = UNMutableNotificationContent()
            content.title = "Hoy toca"
            content.body = text
            content.sound = .default
            let trigger = UNCalendarNotificationTrigger(
                dateMatching: calendar.dateComponents([.year, .month, .day, .hour, .minute], from: fire), repeats: false)
            center.add(UNNotificationRequest(identifier: "reminder-\(date)", content: content, trigger: trigger))
        }
    }
}

/// Aviso de fin de descanso con la app cerrada: se programa al salir de la app con un descanso en marcha y se quita al
/// volver (con la app delante ya vibra ella). Como RestAlert.kt de Android.
enum RestAlert {
    private static let id = "rest"

    static func schedule(at end: Date) {
        cancel()
        let left = end.timeIntervalSinceNow
        guard left > 1 else { return }
        let content = UNMutableNotificationContent()
        content.title = "Se acabó el descanso"
        content.body = "Toca la siguiente serie."
        content.sound = .default
        UNUserNotificationCenter.current().add(UNNotificationRequest(identifier: id, content: content,
            trigger: UNTimeIntervalNotificationTrigger(timeInterval: left, repeats: false)))
    }

    static func cancel() {
        let center = UNUserNotificationCenter.current()
        center.removePendingNotificationRequests(withIdentifiers: [id])
        center.removeDeliveredNotifications(withIdentifiers: [id])
    }

    static func allowed() async -> Bool {
        await UNUserNotificationCenter.current().notificationSettings().authorizationStatus == .authorized
    }
}
