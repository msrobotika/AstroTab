'use strict';

const Pro = (() => {
  // Spec Kit manual, sin CLI: constitución de lógica local, sin dependencias.
  // Criterios: conservar fórmulas, límites, fechas locales y persistencia acotada.
  // Plan y tareas: portar cálculo, planificación, almacenamiento y alarmas.
  // Checklist: revisar unidades, cruces, límites y dependencias omitidas.
  // Verificación estática; no se han ejecutado pruebas por instrucción del usuario.

  const MINUTE = 60000;
  const HOUR = 3600000;
  const DAY = 86400000;
  const RAD = Math.PI / 180;
  const clamp = (x, lo, hi) => Math.max(lo, Math.min(hi, x));
  const atan2d = (y, x) => Math.atan2(y, x) / RAD;
  const asind = x => Math.asin(clamp(x, -1, 1)) / RAD;
  const obliquity = d => 23.4393 - 3.563e-7 * d;
  const midpoint = (a, b) => a + Math.trunc((b - a) / 2);
  const spanHours = s => (s.end - s.start) / HOUR;
  const sum = (items, f) => items.reduce((total, item) => total + f(item), 0);
  const isPlace = p => p != null && typeof p === 'object' && 'lat' in p && 'lon' in p;
  const noopCancelled = () => false;

  function requireValue(condition, message = 'Requisito incumplido.') {
    if (!condition) throw new Error(message);
  }

  function integerIn(value, min, max) {
    return Number.isInteger(value) && value >= min && value <= max;
  }

  function positive(...values) {
    requireValue(values.every(v => Number.isFinite(v) && v > 0));
  }

  function validPlace(place) {
    requireValue(isPlace(place));
    requireValue(Number.isFinite(place.lat) && place.lat >= -90 && place.lat <= 90);
    requireValue(Number.isFinite(place.lon) && place.lon >= -180 && place.lon <= 180);
    return place;
  }

  function record(first, rest, names) {
    if (first !== null && typeof first === 'object' && !Array.isArray(first)) return first;
    const result = {};
    [first, ...rest].forEach((value, index) => {
      if (index < names.length && value !== undefined) result[names[index]] = value;
    });
    return result;
  }

  function localDate(year, month, day, hour = 0) {
    const date = new Date(0);
    date.setFullYear(year, month, day);
    date.setHours(hour, 0, 0, 0);
    return date;
  }

  function targetEq(target) {
    return target.eq || { ra: target.ra, dec: target.dec, dist: 1 };
  }

  function numberText(value, digits = 0) {
    // shortcut: DisplayFormat se sustituye por formato numérico español.
    return new Intl.NumberFormat('es-ES', {
      useGrouping: false,
      minimumFractionDigits: digits,
      maximumFractionDigits: digits
    }).format(value);
  }

  class TimeSpan {
    constructor(start, end) {
      this.start = start;
      this.end = end;
    }

    get hours() {
      return spanHours(this);
    }
  }

  class NightSample {
    constructor(time, sun, moon, moonEq, illumination, sidereal) {
      Object.assign(this, {
        time, sun, moon, moonEq, illumination,
        sidereal: sidereal === undefined ? Astro.gmst(Astro.dayNumber(time)) : sidereal
      });
    }
  }

  class NightTimeline {
    constructor(start, end, samples) {
      Object.assign(this, { start, end, samples });
      this._dark = null;
      this._moonless = null;
    }

    intervals(predicate) {
      const spans = [];
      let begin = null;
      for (let i = 0; i < this.samples.length - 1; i++) {
        const a = this.samples[i];
        const b = this.samples[i + 1];
        const yes = predicate(a);
        if (yes && begin === null) begin = a.time;
        if (begin !== null && (!yes || i === this.samples.length - 2)) {
          spans.push(new TimeSpan(begin, yes ? b.time : a.time));
          begin = null;
        }
      }
      return spans;
    }

    get dark() {
      if (this._dark === null) {
        this._dark = AstroPhoto.above(
          this.samples.map(s => s.time),
          this.samples.map(s => -s.sun),
          18
        );
      }
      return this._dark;
    }

    get moonless() {
      if (this._moonless === null) {
        this._moonless = AstroPhoto.intersect(
          this.dark,
          AstroPhoto.above(
            this.samples.map(s => s.time),
            this.samples.map(s => -s.moon),
            0
          )
        );
      }
      return this._moonless;
    }
  }

  const AstroPhoto = {
    // Precesión media IAU 1976: J2000 a fecha; sin nutación ni movimiento propio.
    precess(eq, time) {
      const t = (time / DAY + 2440587.5 - 2451545) / 36525;
      const zeta = (2306.2181 * t + 0.30188 * t * t + 0.017998 * t * t * t) / 3600;
      const z = (2306.2181 * t + 1.09468 * t * t + 0.018203 * t * t * t) / 3600;
      const theta = (2004.3109 * t - 0.42665 * t * t - 0.041833 * t * t * t) / 3600;
      const a = Astro.cosd(eq.dec) * Astro.sind(eq.ra + zeta);
      const b = Astro.cosd(theta) * Astro.cosd(eq.dec) * Astro.cosd(eq.ra + zeta) -
        Astro.sind(theta) * Astro.sind(eq.dec);
      const c = Astro.sind(theta) * Astro.cosd(eq.dec) * Astro.cosd(eq.ra + zeta) +
        Astro.cosd(theta) * Astro.sind(eq.dec);
      return {
        ...eq,
        ra: Astro.rev(atan2d(a, b) + z),
        dec: atan2d(c, Math.hypot(a, b))
      };
    },

    horizon(eq, time, place) {
      validPlace(place);
      return Astro.toHorizon(
        eq.ra, eq.dec, Astro.gmst(Astro.dayNumber(time)) + place.lon, place.lat
      );
    },

    separation(a, b) {
      return Math.acos(clamp(
        Astro.sind(a.dec) * Astro.sind(b.dec) +
        Astro.cosd(a.dec) * Astro.cosd(b.dec) * Astro.cosd(a.ra - b.ra),
        -1, 1
      )) / RAD;
    },

    nightBounds(now) {
      const date = new Date(now);
      if (date.getHours() < 12) date.setDate(date.getDate() - 1);
      date.setHours(12, 0, 0, 0);
      const start = date.getTime();
      date.setDate(date.getDate() + 1);
      return new TimeSpan(start, date.getTime());
    },

    // Las zonas horarias se omiten: se usa siempre la zona local del navegador.
    // También admite timeline(now, zonaIgnorada, place, stepMinutes).
    timeline(now, place, stepMinutes = 10, legacyStep = 10) {
      if (!isPlace(place) && isPlace(stepMinutes)) {
        place = stepMinutes;
        stepMinutes = legacyStep;
      }
      validPlace(place);
      requireValue(integerIn(stepMinutes, 1, 15));
      const span = AstroPhoto.nightBounds(now);
      const rows = [];
      let time = span.start;
      while (time <= span.end) {
        const d = Astro.dayNumber(time);
        const lst = Astro.gmst(d) + place.lon;
        const sun = Astro.sun(d);
        const moon = Astro.moon(d);
        const lunar = Astro.toHorizon(moon.eq.ra, moon.eq.dec, lst, place.lat);
        const lunarAltitude = lunar.alt - asind(1 / moon.eq.dist) * Astro.cosd(lunar.alt);
        const elong = Astro.cosd(moon.lon - Astro.sunState(d).lon) * Astro.cosd(moon.lat);
        rows.push(new NightSample(
          time,
          Astro.toHorizon(sun.ra, sun.dec, lst, place.lat).alt,
          lunarAltitude,
          moon.eq,
          (1 - clamp(elong, -1, 1)) / 2,
          lst - place.lon
        ));
        if (time === span.end) break;
        time = Math.min(span.end, time + stepMinutes * MINUTE);
      }
      return new NightTimeline(span.start, span.end, rows);
    },

    intervals(night, predicate) {
      return NightTimeline.prototype.intervals.call(night, predicate);
    },

    // Cruces interpolados: no se recalculan Sol y Luna por objeto.
    above(times, values, level) {
      requireValue(times.length === values.length);
      const result = [];
      for (let i = 0; i < times.length - 1; i++) {
        const a = values[i] - level;
        const b = values[i + 1] - level;
        if (a <= 0 && b <= 0) continue;
        const cross = a * b < 0
          ? times[i] + Math.round((times[i + 1] - times[i]) * a / (a - b))
          : times[i];
        const start = a < 0 ? cross : times[i];
        const end = b < 0 ? cross : times[i + 1];
        if (end <= start) continue;
        const last = result[result.length - 1];
        if (last && last.end === start) {
          result[result.length - 1] = new TimeSpan(last.start, end);
        } else {
          result.push(new TimeSpan(start, end));
        }
      }
      return result;
    },

    intersect(a, b) {
      const result = [];
      for (const x of a) {
        for (const y of b) {
          const start = Math.max(x.start, y.start);
          const end = Math.min(x.end, y.end);
          if (end > start) result.push(new TimeSpan(start, end));
        }
      }
      return result;
    },

    transit(eq, start, place) {
      validPlace(place);
      const angle = Astro.rev(eq.ra - Astro.gmst(Astro.dayNumber(start)) - place.lon);
      let time = start + Math.round(angle / 360 * 86164090);
      for (let i = 0; i < 3; i++) {
        const ha = Astro.rev(Astro.gmst(Astro.dayNumber(time)) + place.lon - eq.ra + 180) - 180;
        time -= Math.round(ha / 360 * 86164090);
      }
      return time;
    },

    plan(target, night, place, minimum) {
      validPlace(place);
      requireValue(minimum >= 0 && minimum <= 90);
      const eq = AstroPhoto.precess(targetEq(target), night.start);
      const altitudes = [];
      let useful = 0;
      let height = 0;
      let moon = 0;
      for (let i = 0; i < night.samples.length; i++) {
        const sample = night.samples[i];
        const alt = Astro.toHorizon(
          eq.ra, eq.dec, sample.sidereal + place.lon, place.lat
        ).alt;
        altitudes.push(alt);
        if (i < night.samples.length - 1 && alt >= minimum && sample.sun < -18) {
          const dt = (night.samples[i + 1].time - sample.time) / HOUR;
          useful += dt;
          height += Math.max(0, Astro.sind(alt)) * dt;
          const penalty = sample.moon > 0
            ? sample.illumination *
              (1 - AstroPhoto.separation(eq, sample.moonEq) / 180) *
              Math.max(0, Astro.sind(sample.moon))
            : 0;
          moon += (1 - penalty) * dt;
        }
      }
      const dark = night.dark || AstroPhoto.above(
        night.samples.map(s => s.time), night.samples.map(s => -s.sun), 18
      );
      const windows = AstroPhoto.intersect(
        dark,
        AstroPhoto.above(night.samples.map(s => s.time), altitudes, minimum)
      );
      const altitudeFactor = useful > 0 ? height / useful : 0;
      const moonFactor = useful > 0 ? moon / useful : 0;
      useful = sum(windows, spanHours);
      const timeFactor = clamp(useful / 6, 0, 1);
      const transit = AstroPhoto.transit(eq, night.start, place);
      const count = Math.trunc((night.end - night.start) / (2 * MINUTE)) + 1;
      const sampleStep = night.samples.length > 1
        ? night.samples[1].time - night.start
        : 10 * MINUTE;
      const miniAltitudes = Array.from({ length: count }, (_, i) => {
        const time = night.start +
          Math.trunc((night.end - night.start) * i / Math.max(1, count - 1));
        const index = clamp(
          Math.trunc((time - night.start) / Math.max(1, sampleStep)),
          0, night.samples.length - 1
        );
        const a = night.samples[index];
        const b = night.samples[Math.min(index + 1, night.samples.length - 1)];
        const fraction = b.time === a.time ? 0 : (time - a.time) / (b.time - a.time);
        const sidereal = a.sidereal + Astro.rev(b.sidereal - a.sidereal) * fraction;
        return Astro.toHorizon(eq.ra, eq.dec, sidereal + place.lon, place.lat).alt;
      });
      return {
        target,
        altitudes,
        usefulHours: useful,
        transit,
        transitAltitude: 90 - Math.abs(place.lat - eq.dec),
        score: Math.round(100 * timeFactor * altitudeFactor * moonFactor),
        timeFactor,
        altitudeFactor,
        moonFactor,
        windows,
        miniAltitudes
      };
    },

    galactic(night, place) {
      validPlace(place);
      const eq = AstroPhoto.precess({ ra: 266.4051, dec: -28.9362, dist: 1 }, night.start);
      const predicate = sample =>
        sample.sun < -18 && AstroPhoto.horizon(eq, sample.time, place).alt > 10;
      let peak = null;
      let peakHorizon = null;
      for (const sample of night.samples) {
        if (!predicate(sample)) continue;
        const h = AstroPhoto.horizon(eq, sample.time, place);
        if (peak === null || h.alt > peakHorizon.alt) {
          peak = sample;
          peakHorizon = h;
        }
      }
      return {
        windows: AstroPhoto.intervals(night, predicate),
        highest: peak === null ? null : peak.time,
        altitude: peakHorizon === null ? null : peakHorizon.alt,
        azimuth: peakHorizon === null ? null : peakHorizon.az
      };
    },

    galacticMonths(year, place, legacyPlace) {
      if (!isPlace(place)) place = legacyPlace;
      validPlace(place);
      const result = [];
      for (let month = 1; month <= 12; month++) {
        const mean = sum([5, 15, 25], day => {
          const span = AstroPhoto.nightBounds(localDate(year, month - 1, day, 18).getTime());
          const eq = AstroPhoto.precess({ ra: 266.4051, dec: -28.9362, dist: 1 }, span.start);
          let useful = 0;
          for (let time = span.start; time < span.end;) {
            const next = Math.min(time + 15 * MINUTE, span.end);
            const mid = midpoint(time, next);
            if (AstroPhoto.horizon(Astro.sun(Astro.dayNumber(mid)), mid, place).alt < -18 &&
                AstroPhoto.horizon(eq, mid, place).alt > 10) {
              useful += (next - time) / HOUR;
            }
            time = next;
          }
          return useful;
        }) / 3;
        // Pair de Kotlin representado como [mes, horas].
        result.push([month, mean]);
      }
      return result.sort((a, b) => b[1] - a[1]);
    },

    events(night, place) {
      validPlace(place);
      const events = [];
      function crossings(level, moon, label) {
        for (let i = 0; i < night.samples.length - 1; i++) {
          const a = night.samples[i];
          const b = night.samples[i + 1];
          const av = moon ? a.moon : a.sun;
          const bv = moon ? b.moon : b.sun;
          if ((av >= level) === (bv >= level)) continue;
          const rising = bv > av;
          let lo = a.time;
          let hi = b.time;
          for (let j = 0; j < 14; j++) {
            const mid = midpoint(lo, hi);
            const d = Astro.dayNumber(mid);
            const alt = moon
              ? Astro.moonHorizon(d, Astro.gmst(d) + place.lon, place.lat).alt
              : AstroPhoto.horizon(Astro.sun(d), mid, place).alt;
            if ((alt >= level) === rising) hi = mid;
            else lo = mid;
          }
          events.push({
            label: moon
              ? (rising ? 'Salida de la Luna' : 'Puesta de la Luna')
              : `${label} · ${rising ? 'ascendente' : 'descendente'}`,
            time: midpoint(lo, hi)
          });
        }
      }
      for (const [level, label] of [
        [6, 'Dorada: Sol +6°'],
        [-4, 'Dorada/azul: Sol −4°'],
        [-6, 'Civil/azul: Sol −6°'],
        [-12, 'Náutico: Sol −12°'],
        [-18, 'Astronómico: Sol −18°'],
        [-0.833, 'Salida/puesta del Sol']
      ]) {
        crossings(level, false, label);
      }
      crossings(0, true, 'Luna');
      return events.sort((a, b) => a.time - b.time);
    },

    polar(now, place) {
      validPlace(place);
      const south = place.lat < 0;
      const eq = AstroPhoto.precess(
        south
          ? { ra: 317.195, dec: -88.9565, dist: 1 }
          : { ra: 37.95456067, dec: 89.26410897, dist: 1 },
        now
      );
      const ha = Astro.rev(Astro.gmst(Astro.dayNumber(now)) + place.lon - eq.ra);
      return {
        star: south ? 'Sigma Octantis' : 'Polaris',
        hourAngle: ha,
        clockAngle: Astro.rev(south ? ha : -ha),
        radius: 90 - Math.abs(eq.dec)
      };
    }
  };

  const SensorFormat = Object.freeze(Object.fromEntries([
    ['FULL', 'Completo', 36, 24, 1],
    ['APS_C', 'APS-C 23,6 × 15,7 mm', 23.6, 15.7, 1.5],
    ['CANON', 'APS-C 22,3 × 14,9 mm', 22.3, 14.9, 1.6],
    ['MFT', 'MFT', 17.3, 13, 2],
    ['ONE_INCH', '1 pulgada', 13.2, 8.8, 2.7],
    ['MOBILE', 'Personalizado (editable)', 6.4, 4.8, 5.4],
    ['ASTRO_4_3', 'Astronomía 19,1 × 13 mm', 19.1, 13, 1.87],
    ['ASTRO_1', 'Astronomía 13,2 × 8,8 mm', 13.2, 8.8, 2.7],
    ['ASTRO_533', 'Astronomía 11,3 × 11,3 mm', 11.3, 11.3, 2.7],
    ['ASTRO_585', 'Astronomía 11,2 × 6,3 mm', 11.2, 6.3, 3.4],
    ['ASTRO_174', 'Astronomía 11,3 × 7,1 mm', 11.3, 7.1, 3.2],
    ['ASTRO_290', 'Astronomía 5,6 × 3,2 mm', 5.6, 3.2, 6.7]
  ].map(([name, label, width, height, crop]) =>
    [name, Object.freeze({ name, label, width, height, crop })]
  )));

  class Equipment {
    constructor(options = {}, ...rest) {
      const {
        focal = 50, aperture = 2.8, sensor = SensorFormat.FULL,
        pixel = 4.3, mobileWidth = 6.4, mobileHeight = 4.8,
        tracking = false, equatorial = false
      } = record(options, rest, [
        'focal', 'aperture', 'sensor', 'pixel', 'mobileWidth', 'mobileHeight',
        'tracking', 'equatorial'
      ]);
      positive(focal, aperture, pixel, mobileWidth, mobileHeight);
      const format = SensorFormat[typeof sensor === 'string' ? sensor : sensor && sensor.name];
      requireValue(format !== undefined);
      requireValue(typeof tracking === 'boolean' && typeof equatorial === 'boolean');
      Object.assign(this, {
        focal, aperture, sensor: format, pixel, mobileWidth, mobileHeight, tracking, equatorial
      });
    }

    get width() {
      return this.sensor === SensorFormat.MOBILE ? this.mobileWidth : this.sensor.width;
    }

    get height() {
      return this.sensor === SensorFormat.MOBILE ? this.mobileHeight : this.sensor.height;
    }

    get crop() {
      return Math.hypot(36, 24) / Math.hypot(this.width, this.height);
    }
  }

  Equipment.SensorFormat = SensorFormat;
  const equipmentOf = value => value instanceof Equipment ? value : new Equipment(value);

  function targetDimensions(target) {
    if (target.majorArcmin != null && target.minorArcmin != null) {
      return [target.majorArcmin, target.minorArcmin];
    }
    // shortcut: sin MessierObject, size admite número o «mayor×menor» en arcmin.
    const size = target.size;
    if (typeof size === 'number') {
      return Number.isFinite(size) && size > 0 ? [size, size] : null;
    }
    if (typeof size !== 'string') return null;
    const normalized = size.trim().replace(/,/g, '.').replace(/[′']/g, '');
    const match = normalized.match(
      /^(\d+(?:\.\d+)?)\s*(?:[x×X]\s*(\d+(?:\.\d+)?))?\s*(?:arcmin|min)?$/i
    );
    if (!match) return null;
    const a = Number(match[1]);
    const b = match[2] === undefined ? a : Number(match[2]);
    return a > 0 && b > 0 ? [Math.max(a, b), Math.min(a, b)] : null;
  }

  const Optics = {
    rule500(e) {
      e = equipmentOf(e);
      return 500 / (e.focal * e.crop);
    },

    npf(e, declination) {
      e = equipmentOf(e);
      requireValue(Number.isFinite(declination) && declination >= -90 && declination <= 90);
      return Math.abs(declination) >= 89
        ? null
        : (35 * e.aperture + 30 * e.pixel) / (e.focal * Astro.cosd(declination));
    },

    field(size, focal) {
      requireValue(size > 0 && focal > 0);
      return 2 * Math.atan(size / (2 * focal)) / RAD;
    },

    scale(e) {
      e = equipmentOf(e);
      return 206.264806 * e.pixel / e.focal;
    },

    fit(e, target) {
      e = equipmentOf(e);
      const dimensions = targetDimensions(target);
      if (dimensions === null) return null;
      const widthPercent = dimensions[0] / 60 /
        Optics.field(Math.max(e.width, e.height), e.focal) * 100;
      const heightPercent = dimensions[1] / 60 /
        Optics.field(Math.min(e.width, e.height), e.focal) * 100;
      return {
        widthPercent, heightPercent,
        fits: widthPercent <= 100 && heightPercent <= 100
      };
    }
  };

  const ProTools = {
    focus(e, distanceMetres) {
      e = equipmentOf(e);
      positive(distanceMetres);
      const cocMm = Math.hypot(e.width, e.height) / 1500;
      const h = e.focal * e.focal / (e.aperture * cocMm) + e.focal;
      const s = distanceMetres * 1000;
      requireValue(s > e.focal);
      return {
        cocMm,
        hyperfocalMetres: h / 1000,
        nearMetres: (h - e.focal) * s / (h + s - 2 * e.focal) / 1000,
        farMetres: s >= h ? null : (h - e.focal) * s / (h - s) / 1000
      };
    },

    timelapse(intervalSeconds, eventSeconds, fps, mbPerPhoto) {
      positive(intervalSeconds, eventSeconds, fps, mbPerPhoto);
      // Tomas desde t=0 y estrictamente antes del final.
      const frames = Math.ceil(eventSeconds / intervalSeconds);
      return { frames, clipSeconds: frames / fps, megabytes: frames * mbPerPhoto };
    },

    arc(durationSeconds) {
      requireValue(Number.isFinite(durationSeconds) && durationSeconds >= 0);
      return durationSeconds * 360 / 86164.0905;
    },

    arcSeconds(degrees) {
      requireValue(Number.isFinite(degrees) && degrees >= 0);
      return degrees * 86164.0905 / 360;
    },

    mosaicAxis(coverage, field, overlapPercent) {
      positive(coverage, field);
      requireValue(Number.isFinite(overlapPercent) && overlapPercent >= 0 && overlapPercent <= 95);
      return coverage <= field
        ? 1
        : 1 + Math.ceil((coverage - field) / (field * (1 - overlapPercent / 100)));
    },

    stack(frames, exposureSeconds) {
      requireValue(integerIn(frames, 1, 1000000));
      positive(exposureSeconds);
      return {
        snrGain: Math.sqrt(frames),
        integrationSeconds: frames * exposureSeconds,
        darks: Math.max(20, Math.ceil(Math.sqrt(frames) * 2)),
        flats: 30,
        bias: 50
      };
    }
  };

  const planetNames = Object.freeze(['Mercurio', 'Venus', 'Marte', 'Júpiter', 'Saturno']);
  const showers = Object.freeze([
    ['Cuadrántidas', 1, 4, 120, 230, 49],
    ['Líridas', 4, 22, 18, 271, 34],
    ['Eta Acuáridas', 5, 6, 50, 338, -1],
    ['Delta Acuáridas sur', 7, 30, 25, 340, -16],
    ['Perseidas', 8, 12, 100, 48, 58],
    ['Oriónidas', 10, 21, 20, 95, 16],
    ['Leónidas', 11, 17, 15, 152, 22],
    ['Gemínidas', 12, 14, 150, 112, 33],
    ['Úrsidas', 12, 22, 10, 217, 76]
  ].map(([name, month, day, zhr, ra, dec]) =>
    Object.freeze({ name, month, day, zhr, ra, dec })
  ));

  const ProAstro = {
    planetNames,
    showers,

    planets(night, place) {
      validPlace(place);
      return planetNames.map((name, index) => {
        const altitude = time =>
          AstroPhoto.horizon(Astro.planet(index, Astro.dayNumber(time)).eq, time, place).alt;
        const altitudes = night.samples.map(s => altitude(s.time));
        function crossing(rising) {
          for (let i = 0; i < altitudes.length - 1; i++) {
            if (rising
              ? altitudes[i] < 0 && altitudes[i + 1] >= 0
              : altitudes[i] > 0 && altitudes[i + 1] <= 0) {
              let lo = night.samples[i].time;
              let hi = night.samples[i + 1].time;
              for (let j = 0; j < 14; j++) {
                const mid = midpoint(lo, hi);
                if ((altitude(mid) >= 0) === rising) hi = mid;
                else lo = mid;
              }
              return midpoint(lo, hi);
            }
          }
          return null;
        }
        let transit = AstroPhoto.transit(
          Astro.planet(index, Astro.dayNumber(night.start)).eq, night.start, place
        );
        for (let i = 0; i < 3; i++) {
          transit = AstroPhoto.transit(
            Astro.planet(index, Astro.dayNumber(transit)).eq, night.start, place
          );
        }
        const d = Astro.dayNumber(transit);
        const state = Astro.planet(index, d);
        return {
          index, name, altitudes,
          rise: crossing(true),
          transit,
          set: crossing(false),
          maximum: altitude(transit),
          magnitude: state.mag,
          elongation: AstroPhoto.separation(state.eq, Astro.sun(d))
        };
      });
    },

    moonMonth(now, place, cancelled = noopCancelled, legacyCancelled = noopCancelled) {
      if (!isPlace(place) && isPlace(cancelled)) {
        place = cancelled;
        cancelled = legacyCancelled;
      }
      validPlace(place);
      const date = new Date(now);
      date.setDate(1);
      date.setHours(12, 0, 0, 0);
      const count = localDate(date.getFullYear(), date.getMonth() + 1, 0, 12).getDate();
      const result = [];
      for (let day = 1; day <= count; day++) {
        if (cancelled()) return [];
        date.setDate(day);
        const time = date.getTime();
        const phase = Astro.moonPhase(Astro.dayNumber(time + 8 * HOUR));
        const night = AstroPhoto.timeline(time, place);
        result.push({
          day, time, age: phase.age, illumination: phase.illumination,
          moonlessHours: sum(night.moonless, spanHours)
        });
      }
      return result;
    },

    meteors(now, place, legacyPlace) {
      if (!isPlace(place)) place = legacyPlace;
      validPlace(place);
      const bounds = AstroPhoto.nightBounds(now);
      const year = new Date(now).getFullYear();
      return showers.map(shower => {
        const peak = localDate(year, shower.month - 1, shower.day, 12);
        if (peak.getTime() < now - DAY) peak.setFullYear(peak.getFullYear() + 1);
        const eq = AstroPhoto.precess({ ra: shower.ra, dec: shower.dec, dist: 1 }, now);
        const transit = AstroPhoto.transit(eq, bounds.start, place);
        const midnight = new Date(bounds.start);
        midnight.setDate(midnight.getDate() + 1);
        midnight.setHours(0, 0, 0, 0);
        const time = midnight.getTime();
        const d = Astro.dayNumber(time);
        return {
          shower,
          peak: peak.getTime(),
          radiantAltitude: AstroPhoto.horizon(eq, time, place).alt,
          transit,
          illumination: Astro.moonPhase(d).illumination,
          moonAltitude: Astro.moonHorizon(d, Astro.gmst(d) + place.lon, place.lat).alt
        };
      }).sort((a, b) => a.peak - b.peak);
    }
  };

  function skyEvent(time, title, kind, detail, highlighted = false, summary = '') {
    return { time, title, kind, detail, highlighted, summary };
  }

  const signedAngle = x => Astro.rev(x + 180) - 180;

  function refine(loTime, hiTime, maximum, f) {
    let lo = loTime;
    let hi = hiTime;
    while (hi - lo > MINUTE) {
      const third = Math.trunc((hi - lo) / 3);
      const a = lo + third;
      const b = hi - third;
      if ((f(a) < f(b)) === maximum) lo = a;
      else hi = b;
    }
    return midpoint(lo, hi);
  }

  function eclipticLongitude(eq, d) {
    const e = obliquity(d);
    return Astro.rev(atan2d(
      Astro.sind(eq.ra) * Astro.cosd(e) + Math.tan(eq.dec * RAD) * Astro.sind(e),
      Astro.cosd(eq.ra)
    ));
  }

  function extrema(start, end, maximum, f, step = 3 * HOUR) {
    const result = [];
    let time = start - step;
    let a = f(time);
    let b = f(time + step);
    while (time + step < end) {
      const c = f(time + 2 * step);
      if (maximum ? b > a && b >= c : b < a && b <= c) {
        const peak = refine(time, time + 2 * step, maximum, f);
        if (peak >= start && peak < end &&
            (!result.length || peak - result[result.length - 1] > HOUR)) {
          result.push(peak);
        }
      }
      time += step;
      a = b;
      b = c;
    }
    return result;
  }

  const EventCalendar = {
    eclipses(input) {
      requireValue(typeof input === 'string');
      const result = [];
      for (const line of input.split(/\r\n|\n|\r/)) {
        if (!line.trim() || line.startsWith('#')) continue;
        const row = line.split('\t');
        requireValue(row.length === 5);
        // shortcut: Utc.parse se sustituye por Date.parse para fechas ISO con zona.
        requireValue(/(?:Z|[+-]\d{2}:\d{2})$/i.test(row[0]));
        const time = Date.parse(row[0]);
        requireValue(Number.isFinite(time));
        result.push({ time, solar: row[1] === 'solar', type: row[2], catalog: row[3] });
      }
      return result;
    },

    eclipseEvent(e, place = null) {
      requireValue(typeof e.type === 'string' && e.type.length > 0);
      const kind = ({
        T: 'total', A: 'anular', H: 'híbrido', N: 'penumbral'
      })[e.type[0]] || 'parcial';
      let altitude = null;
      if (place !== null) {
        validPlace(place);
        const d = Astro.dayNumber(e.time);
        altitude = e.solar
          ? AstroPhoto.horizon(Astro.sun(d), e.time, place).alt
          : Astro.moonHorizon(d, Astro.gmst(d) + place.lon, place.lat).alt;
      }
      const visibility = altitude === null
        ? 'Configura ubicación para comprobar horizonte'
        : altitude <= 0
          ? 'No visible en el máximo global: astro bajo el horizonte'
          : !e.solar
            ? 'Visible en el máximo global si el cielo y horizonte lo permiten'
            : 'Sol sobre horizonte: visibilidad local por confirmar; la altura no demuestra que la sombra pase por aquí';
      return skyEvent(
        e.time,
        `Eclipse ${e.solar ? 'solar' : 'lunar'} ${kind}`,
        'eclipse',
        `NASA/GSFC, Fred Espenak, catálogo ${e.catalog}. Máximo global UTC (TD−ΔT de tabla). ${visibility}. ` +
          (altitude === null ? '' : `Altura ${numberText(altitude, 1)}°. `) +
          'En eclipses solares la franja local requiere elementos besselianos; esta comprobación es solo de horizonte.',
        true
      );
    },

    month(now, place = null, eclipses = [], legacyEclipses) {
      if (legacyEclipses !== undefined) {
        place = eclipses;
        eclipses = legacyEclipses;
      }
      if (place !== null) validPlace(place);
      requireValue(Array.isArray(eclipses));
      const current = new Date(now);
      const start = localDate(current.getFullYear(), current.getMonth(), 1).getTime();
      const end = localDate(current.getFullYear(), current.getMonth() + 1, 1).getTime();
      const events = [];
      const accuracy = 'Modelo AstroCYD/Schlyter existente: orientativo, error de horas y separación ~1°; resolución numérica 1 min, sin precisión de almanaque.';

      for (const [target, name] of [
        [0, 'Luna nueva'], [90, 'Cuarto creciente'],
        [180, 'Luna llena'], [270, 'Cuarto menguante']
      ]) {
        let time = start - 3 * HOUR;
        let previous = signedAngle(Astro.moonPhase(Astro.dayNumber(time)).age - target);
        while (time < end) {
          const next = time + 3 * HOUR;
          const value = signedAngle(Astro.moonPhase(Astro.dayNumber(next)).age - target);
          if (previous <= 0 && value > 0 && value - previous < 180) {
            let lo = time;
            let hi = next;
            while (hi - lo > MINUTE) {
              const mid = midpoint(lo, hi);
              if (signedAngle(Astro.moonPhase(Astro.dayNumber(mid)).age - target) > 0) hi = mid;
              else lo = mid;
            }
            const event = midpoint(lo, hi);
            if (event >= start && event < end) {
              events.push(skyEvent(event, name, 'moon', accuracy, target === 0 || target === 180));
            }
          }
          time = next;
          previous = value;
        }
      }

      for (const maximum of [false, true]) {
        for (const time of extrema(
          start, end, maximum, t => Astro.moon(Astro.dayNumber(t)).eq.dist
        )) {
          const distance = Math.round(Astro.moon(Astro.dayNumber(time)).eq.dist * 6378.137);
          events.push(skyEvent(
            time, maximum ? 'Apogeo lunar' : 'Perigeo lunar', 'moon',
            `${distance} km. ${accuracy}`, false, `Distancia ${distance} km`
          ));
        }
      }

      const eq = (index, time) => index === -1
        ? Astro.moon(Astro.dayNumber(time)).eq
        : Astro.planet(index, Astro.dayNumber(time)).eq;
      const label = index => index === -1 ? 'Luna' : planetNames[index];
      // shortcut: SkyControls.cardinals se sustituye por estos ocho puntos.
      const cardinals = ['N', 'NE', 'E', 'SE', 'S', 'SO', 'O', 'NO'];

      for (let a = -1; a <= 3; a++) {
        for (let b = a + 1; b <= 4; b++) {
          const separation = time => AstroPhoto.separation(eq(a, time), eq(b, time));
          for (const time of extrema(start, end, false, separation)) {
            const sep = separation(time);
            if (sep >= 5) continue;
            let summary = `Separación ${numberText(sep, 1)}°`;
            if (place !== null) {
              const h = AstroPhoto.horizon(eq(a, time), time, place);
              summary += h.alt > 0
                ? ` · ${numberText(h.alt)}° sobre el horizonte ${cardinals[Math.trunc((h.az + 22.5) / 45) % 8]}`
                : ' · bajo el horizonte en el mínimo';
            }
            events.push(skyEvent(
              time, `${label(a)} + ${label(b)}`, 'conjunction',
              `Conjunción aparente ${numberText(sep, 1)}° (<5°), mínimo angular geocéntrico. ${accuracy}`,
              true, summary
            ));
          }
        }
      }

      for (let p = 0; p <= 4; p++) {
        const elongation = time => AstroPhoto.separation(eq(p, time), Astro.sun(Astro.dayNumber(time)));
        if (p < 2) {
          for (const time of extrema(start, end, true, elongation)) {
            const d = Astro.dayNumber(time);
            const east = signedAngle(eclipticLongitude(eq(p, time), d) - Astro.sunState(d).lon) > 0;
            events.push(skyEvent(
              time, `${label(p)} · máxima elongación ${east ? 'este' : 'oeste'}`, 'planet',
              `${numberText(elongation(time), 1)}° del Sol. ${accuracy}`, true
            ));
          }
        } else {
          const diff = time => {
            const d = Astro.dayNumber(time);
            return signedAngle(eclipticLongitude(eq(p, time), d) - Astro.sunState(d).lon - 180);
          };
          for (let time = start - 3 * HOUR; time < end; time += 3 * HOUR) {
            const a = diff(time);
            const b = diff(time + 3 * HOUR);
            if (a * b <= 0 && Math.abs(a - b) < 180) {
              let lo = time;
              let hi = time + 3 * HOUR;
              while (hi - lo > MINUTE) {
                const mid = midpoint(lo, hi);
                if ((diff(mid) > 0) === (a > 0)) lo = mid;
                else hi = mid;
              }
              const event = midpoint(lo, hi);
              if (event >= start && event < end) {
                events.push(skyEvent(event, `${label(p)} · oposición`, 'planet', accuracy, true));
              }
            }
          }
        }
      }

      const year = new Date(start).getFullYear();
      for (const shower of showers) {
        const time = localDate(year, shower.month - 1, shower.day, 12).getTime();
        if (time >= start && time < end) {
          events.push(skyEvent(
            time, shower.name, 'meteor',
            `Máximo climatológico IMO, fecha aproximada sin hora anual calculada. THZ ${shower.zhr}; no es una tasa garantizada local.`,
            true, `Máximo aproximado · THZ ${shower.zhr}`
          ));
        }
      }
      for (const eclipse of eclipses) {
        if (eclipse.time >= start && eclipse.time < end) {
          events.push(EventCalendar.eclipseEvent(eclipse, place));
        }
      }
      return events.sort((a, b) => a.time - b.time);
    }
  };

  class TargetRequest {
    constructor(options, integrationMinutes = 30) {
      const value = typeof options === 'string'
        ? { id: options, integrationMinutes }
        : options;
      requireValue(value !== null && typeof value === 'object');
      const { id, integrationMinutes: minutes = 30 } = value;
      requireValue(
        typeof id === 'string' && /^M(?:[1-9][0-9]?|10[0-9]|110)$/.test(id) &&
        integerIn(minutes, 1, 720)
      );
      this.id = id;
      this.integrationMinutes = minutes;
    }
  }

  class ObservingSession {
    constructor(options, ...rest) {
      const { id, name, night, requests } = record(options, rest, ['id', 'name', 'night', 'requests']);
      requireValue(
        typeof id === 'string' && id.length >= 1 && id.length <= 80 &&
        typeof name === 'string' && name.trim().length > 0 && name.length <= 80 &&
        Array.isArray(requests) && requests.length <= 110
      );
      const validated = requests.map(r => r instanceof TargetRequest ? r : new TargetRequest(r));
      requireValue(new Set(validated.map(r => r.id)).size === validated.length);
      requireValue(Number.isSafeInteger(night));
      Object.assign(this, { id, name, night, requests: validated });
    }
  }

  function exposureFor(plan, exposure, equipment) {
    return equipment.tracking
      ? exposure
      : Math.min(exposure, Optics.npf(equipment, plan.target.dec) ?? exposure);
  }

  function usefulWindows(plans) {
    const windows = plans.flatMap(p => p.windows)
      .filter(w => w.end > w.start)
      .sort((a, b) => a.start - b.start);
    const result = [];
    for (const window of windows) {
      const last = result[result.length - 1];
      if (last && window.start <= last.end) {
        result[result.length - 1] = new TimeSpan(last.start, Math.max(last.end, window.end));
      } else {
        result.push(new TimeSpan(window.start, window.end));
      }
    }
    return result;
  }

  const SessionPlanner = {
    DEFAULT_GAP_MINUTES: 10,
    MAX_AUTOMATIC_MINUTES: 240,

    automatic(plans, exposure, pause, equipment, gapMinutes = 10) {
      requireValue(Number.isFinite(exposure) && exposure > 0 &&
        Number.isFinite(pause) && pause >= 0 && integerIn(gapMinutes, 0, 60));
      equipment = equipmentOf(equipment);
      const useful = plans.filter(p => p.score > 0 && p.windows.length > 0);
      const windows = usefulWindows(useful);
      const total = sum(windows, w => w.end - w.start);
      if (total <= 0) return [];
      const byId = new Map(useful.map(p => [p.target.id, p]));
      function choice(requests, plan) {
        return { requests, plan, covered: sum(plan.blocks, b => b.end - b.start) };
      }
      function quality(value) {
        const types = new Set();
        return sum(value.plan.blocks, block => {
          const p = byId.get(block.id);
          const diversity = types.has(p.target.type) ? 0 : 18;
          types.add(p.target.type);
          const meridian = Math.min(
            4, Math.abs(midpoint(block.start, block.end) - p.transit) / HOUR
          ) * 5;
          return (block.end - block.start) / MINUTE * (p.score + diversity - meridian);
        });
      }
      const rank = (a, b) => b.covered - a.covered || quality(b) - quality(a);
      let beam = [choice([], { blocks: [], unscheduled: [] })];
      let best = beam[0];
      for (let depth = 0; depth < useful.length; depth++) {
        const next = [];
        for (const current of beam) {
          const blocks = current.plan.blocks;
          const cursor = blocks.length
            ? blocks[blocks.length - 1].end + gapMinutes * MINUTE
            : windows[0].start;
          const remaining = sum(windows, w => Math.max(0, w.end - Math.max(w.start, cursor)));
          const budget = Math.min(
            SessionPlanner.MAX_AUTOMATIC_MINUTES * MINUTE,
            Math.trunc(remaining / Math.max(1, 3 - depth))
          );
          for (const p of useful) {
            if (current.requests.some(r => r.id === p.target.id)) continue;
            const room = Math.max(0, ...p.windows.map(w =>
              Math.max(0, w.end - Math.max(w.start, cursor))
            ));
            const seconds = Math.min(budget, room) / 1000;
            const perFrame = exposureFor(p, exposure, equipment);
            const frames = Math.min(10000, Math.floor((seconds + pause) / (perFrame + pause)));
            const minutes = Math.min(720, Math.floor(frames * perFrame / 60));
            if (minutes < 1) continue;
            const requests = [...current.requests, new TargetRequest(p.target.id, minutes)];
            const result = SessionPlanner.propose(
              requests, plans, exposure, pause, equipment, gapMinutes
            );
            if (result.blocks.length !== requests.length ||
                result.blocks.some(b =>
                  b.end - b.start > SessionPlanner.MAX_AUTOMATIC_MINUTES * MINUTE)) continue;
            next.push(choice(requests, result));
          }
        }
        next.sort(rank);
        if (!next.length) return best.requests;
        beam = next.slice(0, 12);
        if (rank(beam[0], best) < 0) best = beam[0];
        if (depth >= 2 && best.covered >= total * 0.7) return best.requests;
      }
      return best.requests;
    },

    propose(requests, plans, exposure, pause, equipment, gapMinutes = 10) {
      requireValue(Number.isFinite(exposure) && exposure > 0 &&
        Number.isFinite(pause) && pause >= 0);
      requireValue(integerIn(gapMinutes, 0, 60));
      equipment = equipmentOf(equipment);
      const remaining = requests.map(r => r instanceof TargetRequest ? r : new TargetRequest(r));
      const blocks = [];
      const unscheduled = [];
      const allWindows = plans.flatMap(p => p.windows);
      let cursor = allWindows.length ? Math.min(...allWindows.map(w => w.start)) : 0;

      while (remaining.length) {
        const choices = [];
        for (const request of remaining) {
          const plan = plans.find(p => p.target.id === request.id);
          if (!plan) continue;
          const perFrame = exposureFor(plan, exposure, equipment);
          const desired = Math.min(10000, Math.ceil(request.integrationMinutes * 60 / perFrame));
          let candidate = null;
          for (const window of plan.windows) {
            const start = Math.max(window.start, cursor);
            if (start >= window.end) continue;
            const frames = Math.min(
              desired,
              Math.floor(((window.end - start) / 1000 + pause) / (perFrame + pause))
            );
            if (frames < 1) continue;
            const duration = Math.round((frames * perFrame + (frames - 1) * pause) * 1000);
            const value = {
              request, plan, start, end: start + duration, frames, desired, exposure: perFrame
            };
            if (candidate === null || value.start < candidate.start) candidate = value;
          }
          if (candidate !== null) choices.push(candidate);
        }

        const closes = c => c.plan.windows.find(w => c.start >= w.start && c.end <= w.end).end;
        choices.sort((a, b) =>
          a.start - b.start ||
          closes(a) - closes(b) ||
          b.plan.moonFactor - a.plan.moonFactor ||
          b.plan.score - a.plan.score ||
          Math.abs(midpoint(a.start, a.end) - a.plan.transit) -
            Math.abs(midpoint(b.start, b.end) - b.plan.transit)
        );
        const next = choices[0];
        if (!next) {
          unscheduled.push(...remaining.map(r => r.id));
          break;
        }
        const warnings = [];
        if (next.frames < next.desired) warnings.push('Integración recortada por ventana útil');
        if (equipment.equatorial &&
            next.plan.transit >= next.start - 5 * MINUTE &&
            next.plan.transit <= next.end + 5 * MINUTE) {
          warnings.push('Volteo de meridiano durante el bloque o en los 5 min adyacentes');
        }
        if (next.plan.moonFactor < 0.8) warnings.push('Luna: penalización orientativa');
        if (next.exposure < exposure) warnings.push('Exposición limitada por NPF: sin seguimiento');
        if (!equipment.tracking && Optics.npf(equipment, next.plan.target.dec) === null) {
          warnings.push('NPF no definida cerca del polo: se conserva la exposición de Sesión');
        }
        blocks.push({
          id: next.request.id,
          start: next.start,
          end: next.end,
          frames: next.frames,
          exposure: next.exposure,
          integrationSeconds: next.frames * next.exposure,
          warnings
        });
        cursor = next.end + gapMinutes * MINUTE;
        remaining.splice(remaining.indexOf(next.request), 1);
      }
      return { blocks, unscheduled };
    }
  };

  class SessionPlan {
    constructor(options = {}, ...rest) {
      const {
        lights = 30, exposureSeconds = 30, pauseSeconds = 5,
        darks = 0, flats = 0, flatSeconds = 1
      } = record(options, rest, [
        'lights', 'exposureSeconds', 'pauseSeconds', 'darks', 'flats', 'flatSeconds'
      ]);
      requireValue(integerIn(lights, 1, 10000) &&
        integerIn(darks, 0, 10000) && integerIn(flats, 0, 10000));
      requireValue(
        Number.isFinite(exposureSeconds) && exposureSeconds >= 0.001 && exposureSeconds <= 86400 &&
        Number.isFinite(flatSeconds) && flatSeconds >= 0.1 && flatSeconds <= 86400
      );
      requireValue(Number.isFinite(pauseSeconds) && pauseSeconds >= 0 && pauseSeconds <= 3600);
      Object.assign(this, { lights, exposureSeconds, pauseSeconds, darks, flats, flatSeconds });
    }

    get count() {
      return this.lights + this.darks + this.flats;
    }

    exposure(index) {
      return index < this.lights + this.darks ? this.exposureSeconds : this.flatSeconds;
    }

    kind(index) {
      if (index < this.lights) return 'Luz';
      if (index < this.lights + this.darks) return 'Dark · tapa el objetivo';
      return 'Flat · prepara iluminación';
    }

    get durationMillis() {
      return Math.trunc((
        (this.lights + this.darks) * this.exposureSeconds +
        this.flats * this.flatSeconds +
        (this.count - 1) * this.pauseSeconds
      ) * 1000);
    }

    total() {
      return this.durationMillis;
    }
  }

  class SessionTimer {
    constructor(plan, clock = () => globalThis.performance.now()) {
      requireValue(typeof clock === 'function');
      this.plan = plan instanceof SessionPlan ? plan : new SessionPlan(plan);
      this.clock = clock;
      this.start = clock();
      this.stopped = false;
    }

    stop() {
      this.stopped = true;
    }

    state() {
      let elapsed = Math.max(0, Math.trunc(this.clock() - this.start));
      const total = Math.max(0, this.plan.durationMillis - elapsed);
      const done = () => ({
        done: true, index: this.plan.count - 1, exposing: false,
        remainingMillis: 0, totalRemainingMillis: 0
      });
      if (this.stopped || total === 0) return done();
      for (let index = 0; index < this.plan.count; index++) {
        const exposure = Math.trunc(this.plan.exposure(index) * 1000);
        if (elapsed < exposure) {
          return {
            done: false, index, exposing: true,
            remainingMillis: exposure - elapsed, totalRemainingMillis: total
          };
        }
        elapsed -= exposure;
        const pause = Math.trunc(this.plan.pauseSeconds * 1000);
        if (index < this.plan.count - 1 && elapsed < pause) {
          return {
            done: false, index, exposing: false,
            remainingMillis: pause - elapsed, totalRemainingMillis: total
          };
        }
        elapsed -= pause;
      }
      return done();
    }
  }

  const PhotoSession = {
    SessionPlan,
    SessionTimer,
    total: plan => (plan instanceof SessionPlan ? plan : new SessionPlan(plan)).total()
  };

  const TargetChoice = {
    diverse(plans, count = 3) {
      const selected = [];
      const remaining = [...plans].sort((a, b) => b.score - a.score);
      while (selected.length < count && remaining.length) {
        let best = null;
        let bestScore = -Infinity;
        for (const plan of remaining) {
          if (!selected.every(p =>
            AstroPhoto.separation(targetEq(p.target), targetEq(plan.target)) >= 2)) continue;
          const value = plan.score +
            (selected.some(p => p.target.type === plan.target.type) ? 0 : 18);
          if (best === null || value > bestScore) {
            best = plan;
            bestScore = value;
          }
        }
        if (best === null) break;
        selected.push(best);
        remaining.splice(remaining.indexOf(best), 1);
      }
      return selected;
    }
  };

  const Weather = {
    dewRisk(temperature, dewPoint) {
      if (temperature == null || dewPoint == null) return 'sin datos';
      return temperature - dewPoint < 3 ? 'alto' : 'bajo';
    },

    dewFactor(temperature, dewPoint) {
      return Weather.dewRisk(temperature, dewPoint) === 'alto' ? 0.65 : 1;
    },

    windFactor(wind) {
      if (wind == null) return 1;
      if (wind >= 30) return 0.4;
      if (wind >= 15) return 0.7;
      return 1;
    }
  };

  const temperatureOf = w => w == null ? null : (w.t ?? w.temperature ?? null);
  const dewPointOf = w => w == null ? null : (w.dp ?? w.dewPoint ?? null);

  function weatherAt(hours, time, enabled) {
    if (!enabled || hours == null) return null;
    requireValue(Array.isArray(hours));
    for (let i = hours.length - 1; i >= 0; i--) {
      if (time >= hours[i].time && time < hours[i].time + HOUR) return hours[i];
    }
    return null;
  }

  const Night = {
    factors(time, cloud, sunAlt, moonAlt, illumination, galacticAlt) {
      const darkFactor = sunAlt < -18 ? 1 : sunAlt < -12 ? 0.5 : 0;
      const moonFactor = moonAlt > 0
        ? illumination * (0.5 + 0.5 * Math.min(1, moonAlt / 30))
        : 0;
      return {
        time,
        cloud: cloud ?? null,
        sunAltitude: sunAlt,
        moonAltitude: moonAlt,
        illumination,
        darkFactor,
        moonFactor,
        quality: cloud == null ? null : (1 - cloud / 100) * (1 - 0.8 * moonFactor) * darkFactor,
        galacticVisible: galacticAlt > 10 && sunAlt < -18,
        weather: null,
        dewFactor: 1,
        windFactor: 1
      };
    },

    hour(time, now, location, clouds, networkEnabled = true) {
      validPlace(location);
      // shortcut: el array recibido ya debe estar vigente y corresponder a la ubicación.
      const d = Astro.dayNumber(time);
      const lst = Astro.gmst(d) + location.lon;
      const sun = Astro.sun(d);
      const sunAltitude = Astro.toHorizon(sun.ra, sun.dec, lst, location.lat).alt;
      const moonAltitude = Astro.moonHorizon(d, lst, location.lat).alt;
      const illumination = Astro.moonPhase(d).illumination;
      const weather = weatherAt(clouds, time, networkEnabled);
      const base = Night.factors(
        time, weather == null ? null : weather.cover, sunAltitude, moonAltitude, illumination,
        Astro.toHorizon(266.405, -28.936, lst, location.lat).alt
      );
      const dewFactor = Weather.dewFactor(temperatureOf(weather), dewPointOf(weather));
      const windFactor = Weather.windFactor(weather == null ? null : weather.wind);
      return {
        ...base,
        quality: base.quality === null ? null : base.quality * dewFactor * windFactor,
        weather,
        dewFactor,
        windFactor
      };
    },

    bestWindow(hours, now, bortle) {
      const sorted = [...hours].sort((a, b) => a.time - b.time);
      let best = null;
      let bestQuality = -1;
      for (let i = 0; i < sorted.length - 1; i++) {
        const a = sorted[i];
        const b = sorted[i + 1];
        if (a.time < now || b.time - a.time !== HOUR || a.quality == null || b.quality == null) continue;
        const quality = (a.quality + b.quality) / 2;
        if (quality > bestQuality) {
          bestQuality = quality;
          best = {
            start: a.time,
            end: a.time + 2 * HOUR,
            score: Math.round(quality * (1 - (clamp(bortle, 1, 9) - 1) * 0.06) * 10),
            hours: [a, b]
          };
        }
      }
      return best;
    },

    darknessLabel(hours) {
      if (!hours.length) return 'Sin intervalo calculado';
      if (hours.every(h => h.sunAltitude < -18)) {
        return 'Noche astronómica durante todo el intervalo (posible noche polar)';
      }
      if (!hours.some(h => h.sunAltitude < -18)) return 'Sin noche astronómica en el intervalo';
      return 'Hay noche astronómica (Sol por debajo de −18°)';
    }
  };

  const Forecast = {
    turbulence(jet) {
      if (jet == null) return 'sin datos';
      return jet < 70 ? 'baja' : jet < 140 ? 'moderada' : 'alta';
    },

    layers(c) {
      if (c.low == null || c.mid == null || c.high == null) return null;
      const factor = 100 / Math.max(100, c.low + c.mid + c.high);
      return [c.low * factor, c.mid * factor, c.high * factor];
    },

    // week(now, place, horas, enabled, nightReference, cancelled).
    week(now, ...args) {
      if (!isPlace(args[0]) && isPlace(args[1])) args.shift();
      const [
        place, data = null, enabled = true,
        nightReference = now, cancelled = noopCancelled
      ] = args;
      validPlace(place);
      requireValue(data === null || Array.isArray(data));
      // shortcut: sin metadatos CloudData, vigencia y ubicación las garantiza el llamador.
      const date = new Date(AstroPhoto.nightBounds(nightReference).start);
      const rows = enabled && data !== null ? data : [];
      const result = [];

      for (let day = 0; day < 7; day++) {
        if (cancelled()) return [];
        const night = AstroPhoto.timeline(date.getTime(), place);
        date.setDate(date.getDate() + 1);
        const darkHours = sum(night.dark, spanHours);
        const selected = [];
        for (const weather of rows) {
          const weight = sum(
            AstroPhoto.intersect(night.dark, [new TimeSpan(weather.time, weather.time + HOUR)]),
            spanHours
          );
          if (weight > 0) selected.push({ weather, weight });
        }
        const coveredHours = sum(selected, row => row.weight);
        function mean(field) {
          if (!selected.length) return null;
          let total = 0;
          for (const row of selected) {
            const value = field(row.weather);
            if (value == null) return null;
            total += value * row.weight;
          }
          return total / coveredHours;
        }

        let quality = 0;
        let moon = 0;
        for (const { weather, weight } of selected) {
          const hour = Night.hour(weather.time + HOUR / 2, now, place, data, enabled);
          const moonFactor = 1 - 0.8 * hour.moonFactor;
          moon += hour.moonFactor * weight;
          const rain = 1 - (weather.rain ?? 0) / 100;
          quality += (1 - weather.cover / 100) * moonFactor *
            Weather.dewFactor(temperatureOf(weather), dewPointOf(weather)) *
            Weather.windFactor(weather.wind) * rain * weight;
        }
        const complete = darkHours > 0 && Math.abs(coveredHours - darkHours) < 0.001;
        result.push({
          start: night.start,
          darkHours,
          coveredHours,
          score: complete ? clamp(Math.round(100 * quality / darkHours), 0, 100) : null,
          clouds: mean(w => w.cover),
          moon: coveredHours > 0 ? moon / coveredHours : null,
          dew: mean(w => {
            const t = temperatureOf(w);
            const dp = dewPointOf(w);
            return t == null || dp == null ? null : t - dp;
          }),
          wind: mean(w => w.wind),
          jet: mean(w => w.jet)
        });
      }
      return result;
    },

    best(nights) {
      let best = null;
      for (const night of nights) {
        if (night.score != null && (best === null || night.score > best.score)) best = night;
      }
      return best;
    }
  };

  function utf8Length(text) {
    let bytes = 0;
    for (let i = 0; i < text.length; i++) {
      const code = text.charCodeAt(i);
      if (code < 0x80) bytes++;
      else if (code < 0x800) bytes += 2;
      else if (code >= 0xD800 && code <= 0xDBFF &&
               i + 1 < text.length &&
               text.charCodeAt(i + 1) >= 0xDC00 && text.charCodeAt(i + 1) <= 0xDFFF) {
        bytes += 4;
        i++;
      } else bytes += 3;
    }
    return bytes;
  }

  function storageOf(storage) {
    const value = storage === undefined ? globalThis.localStorage : storage;
    requireValue(value != null &&
      typeof value.getItem === 'function' &&
      typeof value.setItem === 'function' &&
      typeof value.removeItem === 'function', 'Almacenamiento local no disponible');
    return value;
  }

  function readStored(storage, key) {
    const primary = storage.getItem(key);
    return primary === null ? storage.getItem(key + '.bak') : primary;
  }

  function writeStored(storage, key, raw) {
    // shortcut: localStorage no ofrece transacciones entre claves ni fsync.
    // setItem sustituye una clave de forma atómica; se conserva copia anterior.
    const previous = storage.getItem(key);
    try {
      storage.setItem(key + '.tmp', raw);
      if (previous !== null) storage.setItem(key + '.bak', previous);
      storage.setItem(key, raw);
    } catch (error) {
      try { storage.removeItem(key + '.tmp'); } catch (_) { /* Conservar copia. */ }
      throw new Error('No se ha podido guardar');
    }
    try { storage.removeItem(key + '.tmp'); } catch (_) { /* Escritura ya completada. */ }
  }

  class LogEntry {
    constructor(options, ...rest) {
      const { time, target, equipment, frames, exposure, notes } = record(options, rest, [
        'time', 'target', 'equipment', 'frames', 'exposure', 'notes'
      ]);
      requireValue(integerIn(frames, 1, 1000000) && Number.isFinite(exposure) && exposure > 0);
      requireValue([target, equipment, notes].every(v => typeof v === 'string' && v.length <= 4000));
      requireValue(Number.isSafeInteger(time));
      Object.assign(this, { time, target, equipment, frames, exposure, notes });
    }
  }

  function localStamp(time) {
    const date = new Date(time);
    const pad = value => String(value).padStart(2, '0');
    const offset = -date.getTimezoneOffset();
    const sign = offset >= 0 ? '+' : '-';
    const absolute = Math.abs(offset);
    return `${String(date.getFullYear()).padStart(4, '0')}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ` +
      `${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())} ` +
      `${sign}${pad(Math.trunc(absolute / 60))}:${pad(absolute % 60)}`;
  }

  class LogBook {
    constructor(key = 'astrotab.logbook', storage) {
      requireValue(typeof key === 'string' && key.length > 0);
      this.key = key;
      this.storage = storage;
    }

    read() {
      const raw = readStored(storageOf(this.storage), this.key);
      if (raw === null) return [];
      requireValue(utf8Length(raw) <= 4 * 1024 * 1024);
      const rows = JSON.parse(raw);
      requireValue(Array.isArray(rows) && rows.length <= 2000);
      return rows.map(row => new LogEntry(row));
    }

    append(entry) {
      entry = entry instanceof LogEntry ? entry : new LogEntry(entry);
      const entries = [...this.read(), entry];
      requireValue(entries.length <= 2000, 'Límite de 2000 sesiones: exporta la bitácora');
      const raw = JSON.stringify(entries);
      requireValue(utf8Length(raw) <= 4 * 1024 * 1024);
      writeStored(storageOf(this.storage), this.key, raw);
    }

    csv(entries = this.read()) {
      return LogBook.csv(entries);
    }

    static csv(entries) {
      const zone = new Intl.DateTimeFormat().resolvedOptions().timeZone || 'local';
      function cell(value) {
        value = String(value);
        const safe = /^[=+\-@]/.test(value.trimStart()) ? "'" + value : value;
        return '"' + safe.replace(/"/g, '""') + '"';
      }
      return '\uFEFFfecha,zona,objeto,equipo,tomas,exposicion_s,integracion_s,notas\r\n' +
        entries.map(value => {
          const entry = value instanceof LogEntry ? value : new LogEntry(value);
          return [
            localStamp(entry.time), zone, entry.target, entry.equipment,
            entry.frames, entry.exposure, entry.frames * entry.exposure, entry.notes
          ].map(cell).join(',') + '\r\n';
        }).join('');
    }
  }

  class EquipmentProfile {
    constructor(options, ...rest) {
      const { id, name, equipment } = record(options, rest, ['id', 'name', 'equipment']);
      requireValue(typeof id === 'string' && id.length >= 1 && id.length <= 80 &&
        typeof name === 'string' && name.trim().length > 0 && name.length <= 80);
      Object.assign(this, { id, name, equipment: equipmentOf(equipment) });
    }
  }

  function optionalBoolean(value, fallback = false) {
    if (value === true || (typeof value === 'string' && value.toLowerCase() === 'true')) return true;
    if (value === false || (typeof value === 'string' && value.toLowerCase() === 'false')) return false;
    return fallback;
  }

  const EquipmentProfiles = {
    encode(profiles) {
      requireValue(Array.isArray(profiles) && profiles.length >= 1 && profiles.length <= 32);
      const validated = profiles.map(p => p instanceof EquipmentProfile ? p : new EquipmentProfile(p));
      requireValue(new Set(validated.map(p => p.id)).size === validated.length);
      return JSON.stringify(validated.map(profile => {
        const e = profile.equipment;
        return {
          id: profile.id, name: profile.name,
          focal: e.focal, aperture: e.aperture, sensor: e.sensor.name,
          pixel: e.pixel, width: e.mobileWidth, height: e.mobileHeight,
          tracking: e.tracking, equatorial: e.equatorial
        };
      }));
    },

    decode(raw) {
      requireValue(typeof raw === 'string' && raw.length <= 32768);
      const rows = JSON.parse(raw);
      requireValue(Array.isArray(rows) && rows.length >= 1 && rows.length <= 32);
      const profiles = rows.map(row => {
        for (const key of ['focal', 'aperture', 'pixel', 'width', 'height']) positive(row[key]);
        requireValue(typeof row.sensor === 'string' &&
          Object.prototype.hasOwnProperty.call(SensorFormat, row.sensor));
        return new EquipmentProfile({
          id: row.id,
          name: row.name,
          equipment: new Equipment({
            focal: row.focal,
            aperture: row.aperture,
            sensor: row.sensor,
            pixel: row.pixel,
            mobileWidth: row.width,
            mobileHeight: row.height,
            tracking: optionalBoolean(row.tracking),
            equatorial: optionalBoolean(row.equatorial)
          })
        });
      });
      requireValue(new Set(profiles.map(p => p.id)).size === profiles.length);
      return profiles;
    },

    read(key = 'astrotab.equipmentProfiles', storage) {
      const raw = readStored(storageOf(storage), key);
      return raw === null ? [] : EquipmentProfiles.decode(raw);
    },

    save(profiles, key = 'astrotab.equipmentProfiles', storage) {
      const raw = EquipmentProfiles.encode(profiles);
      requireValue(raw.length <= 32768);
      writeStored(storageOf(storage), key, raw);
    }
  };

  class SessionStore {
    constructor(key = 'astrotab.sessions', storage) {
      this.key = key;
      this.storage = storage;
    }

    read() {
      const raw = readStored(storageOf(this.storage), this.key);
      return raw === null ? [] : SessionStore.decode(raw);
    }

    save(value) {
      value = value instanceof ObservingSession ? value : new ObservingSession(value);
      const all = this.read();
      const index = all.findIndex(s => s.id === value.id);
      if (index < 0) all.push(value);
      else all[index] = value;
      requireValue(all.length <= 50);
      writeStored(storageOf(this.storage), this.key, SessionStore.encode(all));
    }

    remove(id) {
      const all = this.read().filter(s => s.id !== id);
      writeStored(storageOf(this.storage), this.key, SessionStore.encode(all));
    }

    static encode(all) {
      requireValue(Array.isArray(all) && all.length <= 50);
      const rows = all.map(value => {
        const session = value instanceof ObservingSession ? value : new ObservingSession(value);
        return {
          id: session.id,
          name: session.name,
          night: session.night,
          targets: session.requests.map(r => ({ id: r.id, minutes: r.integrationMinutes }))
        };
      });
      const raw = JSON.stringify(rows);
      requireValue(utf8Length(raw) <= 512 * 1024);
      return raw;
    }

    static decode(raw) {
      requireValue(typeof raw === 'string' && utf8Length(raw) <= 512 * 1024);
      const rows = JSON.parse(raw);
      requireValue(Array.isArray(rows) && rows.length <= 50);
      return rows.map(row => {
        requireValue(Array.isArray(row.targets) && row.targets.length <= 110);
        return new ObservingSession({
          id: row.id, name: row.name, night: row.night,
          requests: row.targets.map(t => {
            requireValue(integerIn(t.minutes, 1, 720));
            return new TargetRequest(t.id, t.minutes);
          })
        });
      });
    }
  }

  const AlarmKind = Object.freeze({
    ASTRONOMICAL_DUSK: 'ASTRONOMICAL_DUSK',
    OBJECT_ALTITUDE: 'OBJECT_ALTITUDE',
    ISS: 'ISS',
    KP: 'KP',
    MOONRISE: 'MOONRISE'
  });

  class AppAlarm {
    constructor(options, ...rest) {
      const {
        id, kind, label, time,
        sound = false, target = null, enabled = true
      } = record(options, rest, ['id', 'kind', 'label', 'time', 'sound', 'target', 'enabled']);
      requireValue(Number.isInteger(id) && id > 0 &&
        typeof label === 'string' && label.length >= 1 && label.length <= 200);
      requireValue(Object.prototype.hasOwnProperty.call(AlarmKind, kind));
      requireValue(time != null || kind === AlarmKind.KP);
      requireValue(time == null || Number.isSafeInteger(time));
      requireValue(typeof sound === 'boolean' && typeof enabled === 'boolean');
      requireValue(target === null || typeof target === 'string');
      Object.assign(this, { id, kind, label, time: time ?? null, sound, target, enabled });
    }
  }

  function currentKp(data, now) {
    // shortcut: falta DataPolicy.currentKp; el llamador garantiza vigencia del conjunto.
    // Solo se usan muestras no predichas cuyo intervalo contiene el instante actual.
    if (data == null) return null;
    const samples = Array.isArray(data) ? data : data.samples;
    if (!Array.isArray(samples)) return null;
    let best = null;
    for (const sample of samples) {
      const duration = sample.duration ?? 3 * HOUR;
      if (sample.kind !== 'OBSERVED' && sample.kind !== 'ESTIMATED') continue;
      if (sample.time <= now && now < sample.time + duration &&
          (best === null || sample.time >= best.time)) best = sample;
    }
    return best;
  }

  const AlarmPolicy = {
    objectRise(now, eq, place, minimum) {
      validPlace(place);
      requireValue(minimum >= 0 && minimum <= 90);
      const altitude = time => AstroPhoto.horizon(AstroPhoto.precess(eq, time), time, place).alt;
      let time = now;
      let previous = altitude(time);
      while (time < now + 48 * HOUR) {
        const next = time + 5 * MINUTE;
        const value = altitude(next);
        if (previous < minimum && value >= minimum) {
          let lo = time;
          let hi = next;
          for (let i = 0; i < 12; i++) {
            const mid = midpoint(lo, hi);
            if (altitude(mid) >= minimum) hi = mid;
            else lo = mid;
          }
          return midpoint(lo, hi);
        }
        time = next;
        previous = value;
      }
      return null;
    },

    next(kind, now, events = [], plans = [], passes = [], target = null) {
      let times;
      switch (kind) {
        case AlarmKind.ASTRONOMICAL_DUSK:
          times = events
            .filter(e => e.label.startsWith('Astronómico') && e.label.endsWith('descendente'))
            .map(e => e.time);
          break;
        case AlarmKind.MOONRISE:
          times = events.filter(e => e.label === 'Salida de la Luna').map(e => e.time);
          break;
        case AlarmKind.OBJECT_ALTITUDE:
          times = plans.filter(p => p.target.id === target)
            .flatMap(p => p.windows).map(w => w.start);
          break;
        case AlarmKind.ISS:
          // shortcut: SatellitePass se recibe como {tle:{number}, start:{time}}.
          times = passes.filter(p => p.tle.number === 25544).map(p => p.start.time - 5 * MINUTE);
          break;
        case AlarmKind.KP:
          times = [];
          break;
        default:
          throw new Error('Requisito incumplido.');
      }
      const future = times.filter(time => time > now);
      return future.length ? Math.min(...future) : null;
    },

    kpReached(data, now, threshold) {
      const sample = currentKp(data, now);
      return sample !== null && sample.value >= threshold;
    },

    due(alarm, now, kp = false) {
      return alarm.enabled && (
        alarm.time != null
          ? now >= alarm.time && now - alarm.time <= 30 * MINUTE
          : alarm.kind === AlarmKind.KP && kp
      );
    }
  };

  class AlarmStore {
    constructor(key = 'astrotab.alarms', storage) {
      this.key = key;
      this.storage = storage;
    }

    read() {
      try {
        const raw = readStored(storageOf(this.storage), this.key) ?? '[]';
        requireValue(raw.length <= 65536);
        const rows = JSON.parse(raw);
        requireValue(Array.isArray(rows) && rows.length <= 100);
        return rows.map(row => {
          requireValue(Object.prototype.hasOwnProperty.call(row, 'time'));
          return new AppAlarm({
            id: row.id, kind: row.kind, label: row.label, time: row.time,
            sound: optionalBoolean(row.sound),
            target: row.target ?? null,
            enabled: optionalBoolean(row.enabled, true)
          });
        });
      } catch (_) {
        return [];
      }
    }

    save(alarms) {
      requireValue(Array.isArray(alarms) && alarms.length <= 100);
      const validated = alarms.map(a => a instanceof AppAlarm ? a : new AppAlarm(a));
      requireValue(new Set(validated.map(a => a.id)).size === validated.length);
      const raw = JSON.stringify(validated);
      requireValue(raw.length <= 65536);
      writeStored(storageOf(this.storage), this.key, raw);
    }

    add(kind, label, time, sound = false, target = null) {
      const all = this.read();
      const id = all.reduce((maximum, alarm) => Math.max(maximum, alarm.id), 0) + 1;
      const alarm = new AppAlarm({ id, kind, label, time, sound, target });
      this.save([...all, alarm]);
      return alarm;
    }

    remove(id) {
      this.save(this.read().filter(a => a.id !== id));
    }

    setEnabled(id, enabled) {
      const all = this.read().map(alarm =>
        alarm.id === id ? new AppAlarm({ ...alarm, enabled }) : alarm
      );
      this.save(all);
      return all.find(a => a.id === id) || null;
    }
  }

  const AppAlarms = {
    AlarmKind,
    AppAlarm,
    AlarmPolicy,
    AlarmStore,
    objectRise: AlarmPolicy.objectRise,
    next: AlarmPolicy.next,
    kpReached: AlarmPolicy.kpReached,
    due: AlarmPolicy.due
  };

  const Aurora = {
    threshold(latitude, longitude) {
      return Astro.kpNeeded(Astro.geomagLat(latitude, longitude));
    },

    currentAlert(data, now, location) {
      validPlace(location);
      const need = Aurora.threshold(location.lat, location.lon);
      const sample = currentKp(data, now);
      return sample !== null && need <= 9 && sample.value >= need;
    }
  };

  return {
    MINUTE, HOUR, DAY,
    AstroPhoto,
    Optics,
    SensorFormat,
    Equipment,
    ProTools,
    ProAstro,
    EventCalendar,
    SessionPlanner,
    PhotoSession,
    SessionPlan,
    SessionTimer,
    TargetChoice,
    Forecast,
    Weather,
    Night,
    Aurora,
    TimeSpan,
    NightSample,
    NightTimeline,
    TargetRequest,
    ObservingSession,
    SessionStore,
    LogEntry,
    LogBook,
    EquipmentProfile,
    EquipmentProfiles,
    AlarmKind,
    AppAlarm,
    AlarmPolicy,
    AlarmStore,
    AppAlarms,
    // Acceso directo a las operaciones fotográficas principales.
    ...AstroPhoto
  };
})();

if (typeof module!=='undefined') module.exports = Pro;