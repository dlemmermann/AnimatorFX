package com.dlsc.animatorfx.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * A property value that is either static or driven by keyframes.
 */
public sealed interface Animatable<T> permits Animatable.Static, Animatable.Keyframed {

    /** Keyframes closer together than this (seconds) count as being at the same time. */
    double TIME_EPSILON = 1e-6;

    static <T> Animatable<T> of(T value) {
        return new Static<>(value);
    }

    /**
     * Creates a keyframed value. The keyframes are sorted by time.
     *
     * @throws IllegalArgumentException if the list is empty
     */
    static <T> Animatable<T> keyframed(List<Keyframe<T>> keyframes) {
        return new Keyframed<>(keyframes);
    }

    /**
     * Samples the value at the given time in seconds.
     */
    T at(double time);

    boolean isAnimated();

    /**
     * The keyframes sorted by time; empty for a static value.
     */
    List<Keyframe<T>> keyframes();

    /**
     * Adds a keyframe, or replaces the value and easing of the keyframe at the same time
     * (within {@link #TIME_EPSILON}), which keeps its id and time. A static value becomes keyframed.
     */
    Animatable<T> withKeyframe(double time, T value, Easing easing);

    /**
     * Removes a keyframe. Removing the last keyframe yields a static value of that keyframe's
     * value; an unknown id returns this instance.
     */
    Animatable<T> withoutKeyframe(String keyframeId);

    record Static<T>(T value) implements Animatable<T> {

        public Static {
            Objects.requireNonNull(value, "value");
        }

        @Override
        public T at(double time) {
            return value;
        }

        @Override
        public boolean isAnimated() {
            return false;
        }

        @Override
        public List<Keyframe<T>> keyframes() {
            return List.of();
        }

        @Override
        public Animatable<T> withKeyframe(double time, T value, Easing easing) {
            return new Keyframed<>(List.of(newKeyframe(time, value, easing)));
        }

        @Override
        public Animatable<T> withoutKeyframe(String keyframeId) {
            return this;
        }
    }

    record Keyframed<T>(List<Keyframe<T>> keyframes) implements Animatable<T> {

        public Keyframed {
            if (keyframes.isEmpty()) {
                throw new IllegalArgumentException("A keyframed value needs at least one keyframe");
            }
            List<Keyframe<T>> sorted = new ArrayList<>(keyframes);
            sorted.sort(Comparator.comparingDouble(Keyframe::time));
            keyframes = List.copyOf(sorted);
        }

        @Override
        public T at(double time) {
            Keyframe<T> first = keyframes.get(0);
            if (!(time > first.time())) {
                return first.value();
            }
            // index of the last keyframe at or before 'time'
            int low = 0;
            int high = keyframes.size() - 1;
            while (low < high) {
                int mid = (low + high + 1) >>> 1;
                if (keyframes.get(mid).time() <= time) {
                    low = mid;
                } else {
                    high = mid - 1;
                }
            }
            Keyframe<T> a = keyframes.get(low);
            if (low == keyframes.size() - 1 || time == a.time()) {
                return a.value();
            }
            Keyframe<T> b = keyframes.get(low + 1);
            double u = (time - a.time()) / (b.time() - a.time());
            return Interpolation.lerp(a.value(), b.value(), a.easing().apply(u));
        }

        @Override
        public boolean isAnimated() {
            return true;
        }

        @Override
        public Animatable<T> withKeyframe(double time, T value, Easing easing) {
            List<Keyframe<T>> list = new ArrayList<>(keyframes);
            for (int i = 0; i < list.size(); i++) {
                Keyframe<T> k = list.get(i);
                if (Math.abs(k.time() - time) < TIME_EPSILON) {
                    list.set(i, new Keyframe<>(k.id(), k.time(), value, easing));
                    return new Keyframed<>(list);
                }
            }
            list.add(newKeyframe(time, value, easing));
            return new Keyframed<>(list);
        }

        @Override
        public Animatable<T> withoutKeyframe(String keyframeId) {
            List<Keyframe<T>> list = new ArrayList<>(keyframes);
            if (!list.removeIf(k -> k.id().equals(keyframeId))) {
                return this;
            }
            if (list.isEmpty()) {
                return new Static<>(keyframes.get(keyframes.size() - 1).value());
            }
            return new Keyframed<>(list);
        }
    }

    private static <T> Keyframe<T> newKeyframe(double time, T value, Easing easing) {
        return new Keyframe<>(UUID.randomUUID().toString(), time, value, easing);
    }
}
