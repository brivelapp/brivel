import Foundation
import Security
import Shared

/// Datos de la app en UserDefaults: solo en el móvil. Las series de cada actividad propia y la grabación en curso van en
/// ficheros (Application Support): con decenas de actividades pesarían varios megas en UserDefaults.
final class DefaultsStore: KeyValueStore {
    private let defaults = UserDefaults.standard
    private let folder: URL = {
        let base = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0].appendingPathComponent("activities")
        try? FileManager.default.createDirectory(at: base, withIntermediateDirectories: true)
        return base
    }()

    private func file(_ key: String) -> URL? {
        guard key.hasPrefix("brivel.activity.") else { return nil }
        return folder.appendingPathComponent(key.replacingOccurrences(of: "[^A-Za-z0-9.-]", with: "_", options: .regularExpression))
    }

    func read(key: String) -> String? {
        if let file = file(key) { return try? String(contentsOf: file, encoding: .utf8) }
        return defaults.string(forKey: key)
    }

    func write(key: String, value: String?) {
        if let file = file(key) {
            if let value { try? value.write(to: file, atomically: true, encoding: .utf8) } else { try? FileManager.default.removeItem(at: file) }
        } else if let value { defaults.set(value, forKey: key) } else { defaults.removeObject(forKey: key) }
    }
}

/// La clave de intervals.icu en el Llavero de iOS, solo en este dispositivo y sin copia en iCloud.
final class KeychainStore: KeyValueStore {
    private let service = "app.brivel"

    private func query(_ key: String) -> [String: Any] {
        [kSecClass as String: kSecClassGenericPassword, kSecAttrService as String: service, kSecAttrAccount as String: key]
    }

    func read(key: String) -> String? {
        var q = query(key)
        q[kSecReturnData as String] = true
        q[kSecMatchLimit as String] = kSecMatchLimitOne
        var result: AnyObject?
        guard SecItemCopyMatching(q as CFDictionary, &result) == errSecSuccess, let data = result as? Data else { return nil }
        return String(data: data, encoding: .utf8)
    }

    func write(key: String, value: String?) {
        SecItemDelete(query(key) as CFDictionary)
        guard let value else { return }
        var q = query(key)
        q[kSecValueData as String] = Data(value.utf8)
        q[kSecAttrAccessible as String] = kSecAttrAccessibleWhenUnlockedThisDeviceOnly
        SecItemAdd(q as CFDictionary, nil)
    }
}
