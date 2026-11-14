import CoreLocation
import Shared

/// Graba con el GPS del iPhone (docs/VISION.md §9, bloque G): entrega cada punto al núcleo (Recorder.kt), que lo filtra y
/// lo guarda. La ubicación solo se pide mientras se graba y no sale del iPhone. Sigue con la pantalla apagada porque la
/// grabación empieza con la app abierta y la app declara el modo de ubicación en segundo plano (project.yml).
final class Recorder: NSObject, CLLocationManagerDelegate {
    static let shared = Recorder()
    private let manager = CLLocationManager()
    private var core: Shared.BrivelApp?

    override private init() {
        super.init()
        manager.delegate = self
        manager.desiredAccuracy = kCLLocationAccuracyBest
        manager.activityType = .fitness
        manager.pausesLocationUpdatesAutomatically = false
    }

    /// El usuario negó la ubicación: se cambia en Ajustes.
    var denied: Bool { manager.authorizationStatus == .denied || manager.authorizationStatus == .restricted }

    /// Empieza a recibir puntos (pide el permiso la primera vez). Llamarlo otra vez no hace daño.
    func start(_ core: Shared.BrivelApp) {
        self.core = core
        if manager.authorizationStatus == .notDetermined { manager.requestWhenInUseAuthorization() }
        // Sin el modo declarado en Info.plist, activar esto cerraría la app: se comprueba antes
        let modes = Bundle.main.object(forInfoDictionaryKey: "UIBackgroundModes") as? [String] ?? []
        if modes.contains("location") {
            manager.allowsBackgroundLocationUpdates = true
            manager.showsBackgroundLocationIndicator = true
        }
        manager.startUpdatingLocation()
    }

    func stop() {
        manager.stopUpdatingLocation()
        manager.allowsBackgroundLocationUpdates = false
    }

    // El gestor se crea en el hilo principal y avisa en él
    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        for l in locations {
            core?.recordPoint(time: Int64(l.timestamp.timeIntervalSince1970), lat: l.coordinate.latitude, lon: l.coordinate.longitude,
                              altitude: l.verticalAccuracy > 0 ? KotlinDouble(double: l.altitude) : nil,
                              accuracy: l.horizontalAccuracy >= 0 ? l.horizontalAccuracy : 999)
        }
    }

    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {}  // sin señal: se sigue esperando
}
