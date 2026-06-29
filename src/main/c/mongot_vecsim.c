#define EXPORT __attribute__((visibility("default")))
#define HIDDEN __attribute__((visibility("hidden")))

#include <math.h>
#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#if defined(__x86_64__)
#include <immintrin.h>
#endif

#if defined(__ARM_NEON)
#include <arm_neon.h>
#if defined(__APPLE__)
#include <sys/sysctl.h>
#elif defined(__linux__)
#include <sys/auxv.h>
#include <asm/hwcap.h>
#endif
#endif

#if defined(__aarch64__) && defined(__ARM_FEATURE_SVE)
#include <arm_sve.h>
#endif

inline HIDDEN float sim_cos_combine(int32_t dot, int32_t norm_a, int32_t norm_b) {
  // Return the lowest possible angular similarity score if we would divide by zero.
  return norm_a == 0 || norm_b == 0 ? -1.0 : (float)dot / sqrtf((float)norm_a * (float)norm_b);
}

// --- Scalar implementations ---

HIDDEN int32_t sim_dot_i8_scalar(const int8_t* a, const int8_t* b, size_t n) {
  int32_t sum = 0;
  for (size_t i = 0; i < n; ++i) {
    sum += a[i] * b[i];
  }
  return sum;
}

HIDDEN int32_t sim_l2_i8_scalar(const int8_t* a, const int8_t* b, size_t n) {
  int32_t sum = 0;
  for (size_t i = 0; i < n; ++i) {
    int32_t diff = (int32_t)a[i] - (int32_t)b[i];
    sum += diff * diff;
  }
  return sum;
}

HIDDEN float sim_cos_i8_scalar(const int8_t* a, const int8_t* b, size_t n) {
  int32_t dot = 0, norm_a = 0, norm_b = 0;
  for (size_t i = 0; i < n; ++i) {
    dot    += (int32_t)a[i] * (int32_t)b[i];
    norm_a += (int32_t)a[i] * (int32_t)a[i];
    norm_b += (int32_t)b[i] * (int32_t)b[i];
  }
  return sim_cos_combine(dot, norm_a, norm_b);
}

// --- NEON implementation ---

#if defined(__ARM_NEON) && defined(__ARM_FEATURE_DOTPROD)
static int has_dotprod(void) {
#if defined(__APPLE__)
  int val = 0;
  size_t len = sizeof(val);
  if (sysctlbyname("hw.optional.arm.FEAT_DotProd", &val, &len, NULL, 0) == 0)
    return val;
  return 0;
#elif defined(__linux__)
#ifndef HWCAP_ASIMDDP
#define HWCAP_ASIMDDP (1 << 20)
#endif
  return (getauxval(AT_HWCAP) & HWCAP_ASIMDDP) != 0;
#else
  return 0;
#endif
}

HIDDEN __attribute__((target("+dotprod"))) int32_t sim_dot_i8_dotprod(const int8_t* a, const int8_t* b, size_t n) {
  int32x4_t acc0 = vdupq_n_s32(0);
  int32x4_t acc1 = vdupq_n_s32(0);
  int32x4_t acc2 = vdupq_n_s32(0);
  int32x4_t acc3 = vdupq_n_s32(0);
  size_t i = 0;
  for (; i + 63 < n; i += 64) {
    acc0 = vdotq_s32(acc0, vld1q_s8(a + i),      vld1q_s8(b + i));
    acc1 = vdotq_s32(acc1, vld1q_s8(a + i + 16), vld1q_s8(b + i + 16));
    acc2 = vdotq_s32(acc2, vld1q_s8(a + i + 32), vld1q_s8(b + i + 32));
    acc3 = vdotq_s32(acc3, vld1q_s8(a + i + 48), vld1q_s8(b + i + 48));
  }
  acc0 = vaddq_s32(vaddq_s32(acc0, acc1), vaddq_s32(acc2, acc3));
  for (; i + 15 < n; i += 16)
    acc0 = vdotq_s32(acc0, vld1q_s8(a + i), vld1q_s8(b + i));
  int32_t sum = vaddvq_s32(acc0);
  for (; i < n; ++i) sum += (int32_t)a[i] * (int32_t)b[i];
  return sum;
}

HIDDEN __attribute__((target("+dotprod"))) int32_t sim_l2_i8_dotprod(const int8_t* a, const int8_t* b, size_t n) {
  uint32x4_t acc0 = vdupq_n_u32(0);
  uint32x4_t acc1 = vdupq_n_u32(0);
  uint32x4_t acc2 = vdupq_n_u32(0);
  uint32x4_t acc3 = vdupq_n_u32(0);
  size_t i = 0;
  for (; i + 63 < n; i += 64) {
    uint8x16_t d0 = vreinterpretq_u8_s8(vabdq_s8(vld1q_s8(a + i),      vld1q_s8(b + i)));
    uint8x16_t d1 = vreinterpretq_u8_s8(vabdq_s8(vld1q_s8(a + i + 16), vld1q_s8(b + i + 16)));
    uint8x16_t d2 = vreinterpretq_u8_s8(vabdq_s8(vld1q_s8(a + i + 32), vld1q_s8(b + i + 32)));
    uint8x16_t d3 = vreinterpretq_u8_s8(vabdq_s8(vld1q_s8(a + i + 48), vld1q_s8(b + i + 48)));
    acc0 = vdotq_u32(acc0, d0, d0);
    acc1 = vdotq_u32(acc1, d1, d1);
    acc2 = vdotq_u32(acc2, d2, d2);
    acc3 = vdotq_u32(acc3, d3, d3);
  }
  acc0 = vaddq_u32(vaddq_u32(acc0, acc1), vaddq_u32(acc2, acc3));
  for (; i + 15 < n; i += 16) {
    uint8x16_t d = vreinterpretq_u8_s8(vabdq_s8(vld1q_s8(a + i), vld1q_s8(b + i)));
    acc0 = vdotq_u32(acc0, d, d);
  }
  int32_t sum = (int32_t)vaddvq_u32(acc0);
  for (; i < n; ++i) {
    int32_t diff = (int32_t)a[i] - (int32_t)b[i];
    sum += diff * diff;
  }
  return sum;
}

HIDDEN __attribute__((target("+dotprod"))) float sim_cos_i8_dotprod(const int8_t* a, const int8_t* b, size_t n) {
  int32x4_t ab0 = vdupq_n_s32(0), ab1 = vdupq_n_s32(0), ab2 = vdupq_n_s32(0), ab3 = vdupq_n_s32(0);
  int32x4_t aa0 = vdupq_n_s32(0), aa1 = vdupq_n_s32(0), aa2 = vdupq_n_s32(0), aa3 = vdupq_n_s32(0);
  int32x4_t bb0 = vdupq_n_s32(0), bb1 = vdupq_n_s32(0), bb2 = vdupq_n_s32(0), bb3 = vdupq_n_s32(0);
  size_t i = 0;
  for (; i + 63 < n; i += 64) {
    int8x16_t a0 = vld1q_s8(a + i),      b0 = vld1q_s8(b + i);
    int8x16_t a1 = vld1q_s8(a + i + 16), b1 = vld1q_s8(b + i + 16);
    int8x16_t a2 = vld1q_s8(a + i + 32), b2 = vld1q_s8(b + i + 32);
    int8x16_t a3 = vld1q_s8(a + i + 48), b3 = vld1q_s8(b + i + 48);
    ab0 = vdotq_s32(ab0, a0, b0); aa0 = vdotq_s32(aa0, a0, a0); bb0 = vdotq_s32(bb0, b0, b0);
    ab1 = vdotq_s32(ab1, a1, b1); aa1 = vdotq_s32(aa1, a1, a1); bb1 = vdotq_s32(bb1, b1, b1);
    ab2 = vdotq_s32(ab2, a2, b2); aa2 = vdotq_s32(aa2, a2, a2); bb2 = vdotq_s32(bb2, b2, b2);
    ab3 = vdotq_s32(ab3, a3, b3); aa3 = vdotq_s32(aa3, a3, a3); bb3 = vdotq_s32(bb3, b3, b3);
  }
  int32x4_t ab = vaddq_s32(vaddq_s32(ab0, ab1), vaddq_s32(ab2, ab3));
  int32x4_t aa = vaddq_s32(vaddq_s32(aa0, aa1), vaddq_s32(aa2, aa3));
  int32x4_t bb = vaddq_s32(vaddq_s32(bb0, bb1), vaddq_s32(bb2, bb3));
  for (; i + 15 < n; i += 16) {
    int8x16_t ai = vld1q_s8(a + i), bi = vld1q_s8(b + i);
    ab = vdotq_s32(ab, ai, bi); aa = vdotq_s32(aa, ai, ai); bb = vdotq_s32(bb, bi, bi);
  }
  int32_t dot    = vaddvq_s32(ab);
  int32_t norm_a = vaddvq_s32(aa);
  int32_t norm_b = vaddvq_s32(bb);
  for (; i < n; ++i) {
    dot    += (int32_t)a[i] * (int32_t)b[i];
    norm_a += (int32_t)a[i] * (int32_t)a[i];
    norm_b += (int32_t)b[i] * (int32_t)b[i];
  }
  return sim_cos_combine(dot, norm_a, norm_b);
}
#endif  // __ARM_NEON && __ARM_FEATURE_DOTPROD

// --- aarch64 SVE implementation ---

#if defined(__aarch64__) && defined(__ARM_FEATURE_SVE)
static int has_sve(void) {
#if defined(__linux__)
#ifndef HWCAP_SVE
#define HWCAP_SVE (1 << 22)
#endif
  return (getauxval(AT_HWCAP) & HWCAP_SVE) != 0;
#else
  return 0;  // Apple SVE is 128-bit (same as NEON); no throughput benefit
#endif
}

HIDDEN __attribute__((target("+sve"))) int32_t sim_dot_i8_sve(const int8_t* a, const int8_t* b, size_t n) {
  svint32_t acc0 = svdup_n_s32(0), acc1 = svdup_n_s32(0);
  const size_t vl = svcntb();
  size_t i = 0;
  for (; i + 2 * vl <= n; i += 2 * vl) {
    acc0 = svdot_s32(acc0, svld1_s8(svptrue_b8(), a + i),      svld1_s8(svptrue_b8(), b + i));
    acc1 = svdot_s32(acc1, svld1_s8(svptrue_b8(), a + i + vl), svld1_s8(svptrue_b8(), b + i + vl));
  }
  acc0 = svadd_s32_x(svptrue_b32(), acc0, acc1);
  for (; i + vl <= n; i += vl)
    acc0 = svdot_s32(acc0, svld1_s8(svptrue_b8(), a + i), svld1_s8(svptrue_b8(), b + i));
  if (i < n) {
    svbool_t pg = svwhilelt_b8_u64(i, (uint64_t)n);
    acc0 = svdot_s32(acc0, svld1_s8(pg, a + i), svld1_s8(pg, b + i));
  }
  return (int32_t)svaddv_s32(svptrue_b32(), acc0);
}

HIDDEN __attribute__((target("+sve"))) int32_t sim_l2_i8_sve(const int8_t* a, const int8_t* b, size_t n) {
  svuint32_t acc0 = svdup_n_u32(0), acc1 = svdup_n_u32(0);
  const size_t vl = svcntb();
  size_t i = 0;
  for (; i + 2 * vl <= n; i += 2 * vl) {
    svuint8_t d0 = svreinterpret_u8_s8(svabd_s8_x(svptrue_b8(), svld1_s8(svptrue_b8(), a + i),      svld1_s8(svptrue_b8(), b + i)));
    svuint8_t d1 = svreinterpret_u8_s8(svabd_s8_x(svptrue_b8(), svld1_s8(svptrue_b8(), a + i + vl), svld1_s8(svptrue_b8(), b + i + vl)));
    acc0 = svdot_u32(acc0, d0, d0);
    acc1 = svdot_u32(acc1, d1, d1);
  }
  acc0 = svadd_u32_x(svptrue_b32(), acc0, acc1);
  for (; i + vl <= n; i += vl) {
    svuint8_t d = svreinterpret_u8_s8(svabd_s8_x(svptrue_b8(), svld1_s8(svptrue_b8(), a + i), svld1_s8(svptrue_b8(), b + i)));
    acc0 = svdot_u32(acc0, d, d);
  }
  if (i < n) {
    svbool_t pg = svwhilelt_b8_u64(i, (uint64_t)n);
    // Inactive lanes from svld1 are zero, so |0-0|=0 contributes nothing.
    svuint8_t d = svreinterpret_u8_s8(svabd_s8_x(svptrue_b8(), svld1_s8(pg, a + i), svld1_s8(pg, b + i)));
    acc0 = svdot_u32(acc0, d, d);
  }
  return (int32_t)svaddv_u32(svptrue_b32(), acc0);
}

HIDDEN __attribute__((target("+sve"))) float sim_cos_i8_sve(const int8_t* a, const int8_t* b, size_t n) {
  svint32_t ab0 = svdup_n_s32(0), ab1 = svdup_n_s32(0);
  svint32_t aa0 = svdup_n_s32(0), aa1 = svdup_n_s32(0);
  svint32_t bb0 = svdup_n_s32(0), bb1 = svdup_n_s32(0);
  const size_t vl = svcntb();
  size_t i = 0;
  for (; i + 2 * vl <= n; i += 2 * vl) {
    svint8_t ai = svld1_s8(svptrue_b8(), a + i),      bi = svld1_s8(svptrue_b8(), b + i);
    svint8_t aj = svld1_s8(svptrue_b8(), a + i + vl), bj = svld1_s8(svptrue_b8(), b + i + vl);
    ab0 = svdot_s32(ab0, ai, bi); aa0 = svdot_s32(aa0, ai, ai); bb0 = svdot_s32(bb0, bi, bi);
    ab1 = svdot_s32(ab1, aj, bj); aa1 = svdot_s32(aa1, aj, aj); bb1 = svdot_s32(bb1, bj, bj);
  }
  svint32_t ab = svadd_s32_x(svptrue_b32(), ab0, ab1);
  svint32_t aa = svadd_s32_x(svptrue_b32(), aa0, aa1);
  svint32_t bb = svadd_s32_x(svptrue_b32(), bb0, bb1);
  for (; i + vl <= n; i += vl) {
    svint8_t ai = svld1_s8(svptrue_b8(), a + i), bi = svld1_s8(svptrue_b8(), b + i);
    ab = svdot_s32(ab, ai, bi); aa = svdot_s32(aa, ai, ai); bb = svdot_s32(bb, bi, bi);
  }
  if (i < n) {
    svbool_t pg = svwhilelt_b8_u64(i, (uint64_t)n);
    svint8_t ai = svld1_s8(pg, a + i), bi = svld1_s8(pg, b + i);
    ab = svdot_s32(ab, ai, bi); aa = svdot_s32(aa, ai, ai); bb = svdot_s32(bb, bi, bi);
  }
  return sim_cos_combine(
    (int32_t)svaddv_s32(svptrue_b32(), ab),
    (int32_t)svaddv_s32(svptrue_b32(), aa),
    (int32_t)svaddv_s32(svptrue_b32(), bb)
  );
}
#endif  // __aarch64__ && __ARM_FEATURE_SVE

// --- x86_64 AVX-512VNNI implementation ---

#if defined(__x86_64__)
static int has_avx512vnni(void) {
  return __builtin_cpu_supports("avx512vnni") && __builtin_cpu_supports("avx512bw");
}

// Load 32 signed i8 via a 256-bit load, widen to i16, then accumulate pairs
// into i32 with vpdpwssd (AVX-512VNNI).  Avoids the XOR-128 bias correction
// needed by vpdpbusd and uses fewer zmm registers than a 512-bit load path.
HIDDEN __attribute__((target("avx512f,avx512bw,avx512vnni"))) int32_t sim_dot_i8_avx512vnni(const int8_t* a, const int8_t* b, size_t n) {
  __m512i acc0 = _mm512_setzero_si512();
  __m512i acc1 = _mm512_setzero_si512();
  size_t i = 0;
  for (; i + 63 < n; i += 64) {
    __m512i a0 = _mm512_cvtepi8_epi16(_mm256_loadu_si256((const __m256i*)(a + i)));
    __m512i b0 = _mm512_cvtepi8_epi16(_mm256_loadu_si256((const __m256i*)(b + i)));
    __m512i a1 = _mm512_cvtepi8_epi16(_mm256_loadu_si256((const __m256i*)(a + i + 32)));
    __m512i b1 = _mm512_cvtepi8_epi16(_mm256_loadu_si256((const __m256i*)(b + i + 32)));
    acc0 = _mm512_dpwssd_epi32(acc0, a0, b0);
    acc1 = _mm512_dpwssd_epi32(acc1, a1, b1);
  }
  acc0 = _mm512_add_epi32(acc0, acc1);
  for (; i + 31 < n; i += 32) {
    __m512i ai = _mm512_cvtepi8_epi16(_mm256_loadu_si256((const __m256i*)(a + i)));
    __m512i bi = _mm512_cvtepi8_epi16(_mm256_loadu_si256((const __m256i*)(b + i)));
    acc0 = _mm512_dpwssd_epi32(acc0, ai, bi);
  }
  int32_t sum = _mm512_reduce_add_epi32(acc0);
  for (; i < n; ++i) sum += (int32_t)a[i] * (int32_t)b[i];
  return sum;
}

HIDDEN __attribute__((target("avx512f,avx512bw,avx512vnni"))) int32_t sim_l2_i8_avx512vnni(const int8_t* a, const int8_t* b, size_t n) {
  __m512i acc0 = _mm512_setzero_si512();
  __m512i acc1 = _mm512_setzero_si512();
  size_t i = 0;
  for (; i + 63 < n; i += 64) {
    __m512i a0 = _mm512_cvtepi8_epi16(_mm256_loadu_si256((const __m256i*)(a + i)));
    __m512i b0 = _mm512_cvtepi8_epi16(_mm256_loadu_si256((const __m256i*)(b + i)));
    __m512i a1 = _mm512_cvtepi8_epi16(_mm256_loadu_si256((const __m256i*)(a + i + 32)));
    __m512i b1 = _mm512_cvtepi8_epi16(_mm256_loadu_si256((const __m256i*)(b + i + 32)));
    __m512i d0 = _mm512_sub_epi16(a0, b0);
    __m512i d1 = _mm512_sub_epi16(a1, b1);
    acc0 = _mm512_dpwssd_epi32(acc0, d0, d0);
    acc1 = _mm512_dpwssd_epi32(acc1, d1, d1);
  }
  acc0 = _mm512_add_epi32(acc0, acc1);
  for (; i + 31 < n; i += 32) {
    __m512i di = _mm512_sub_epi16(_mm512_cvtepi8_epi16(_mm256_loadu_si256((const __m256i*)(a + i))),
                                  _mm512_cvtepi8_epi16(_mm256_loadu_si256((const __m256i*)(b + i))));
    acc0 = _mm512_dpwssd_epi32(acc0, di, di);
  }
  int32_t sum = _mm512_reduce_add_epi32(acc0);
  for (; i < n; ++i) {
    int32_t diff = (int32_t)a[i] - (int32_t)b[i];
    sum += diff * diff;
  }
  return sum;
}

HIDDEN __attribute__((target("avx512f,avx512bw,avx512vnni"))) float sim_cos_i8_avx512vnni(const int8_t* a, const int8_t* b, size_t n) {
  __m512i ab0 = _mm512_setzero_si512(), ab1 = _mm512_setzero_si512();
  __m512i aa0 = _mm512_setzero_si512(), aa1 = _mm512_setzero_si512();
  __m512i bb0 = _mm512_setzero_si512(), bb1 = _mm512_setzero_si512();
  size_t i = 0;
  for (; i + 63 < n; i += 64) {
    __m512i a0 = _mm512_cvtepi8_epi16(_mm256_loadu_si256((const __m256i*)(a + i)));
    __m512i b0 = _mm512_cvtepi8_epi16(_mm256_loadu_si256((const __m256i*)(b + i)));
    __m512i a1 = _mm512_cvtepi8_epi16(_mm256_loadu_si256((const __m256i*)(a + i + 32)));
    __m512i b1 = _mm512_cvtepi8_epi16(_mm256_loadu_si256((const __m256i*)(b + i + 32)));
    ab0 = _mm512_dpwssd_epi32(ab0, a0, b0);
    aa0 = _mm512_dpwssd_epi32(aa0, a0, a0);
    bb0 = _mm512_dpwssd_epi32(bb0, b0, b0);
    ab1 = _mm512_dpwssd_epi32(ab1, a1, b1);
    aa1 = _mm512_dpwssd_epi32(aa1, a1, a1);
    bb1 = _mm512_dpwssd_epi32(bb1, b1, b1);
  }
  __m512i ab = _mm512_add_epi32(ab0, ab1);
  __m512i aa = _mm512_add_epi32(aa0, aa1);
  __m512i bb = _mm512_add_epi32(bb0, bb1);
  for (; i + 31 < n; i += 32) {
    __m512i ai = _mm512_cvtepi8_epi16(_mm256_loadu_si256((const __m256i*)(a + i)));
    __m512i bi = _mm512_cvtepi8_epi16(_mm256_loadu_si256((const __m256i*)(b + i)));
    ab = _mm512_dpwssd_epi32(ab, ai, bi);
    aa = _mm512_dpwssd_epi32(aa, ai, ai);
    bb = _mm512_dpwssd_epi32(bb, bi, bi);
  }
  int32_t dot    = _mm512_reduce_add_epi32(ab);
  int32_t norm_a = _mm512_reduce_add_epi32(aa);
  int32_t norm_b = _mm512_reduce_add_epi32(bb);
  for (; i < n; ++i) {
    dot    += (int32_t)a[i] * (int32_t)b[i];
    norm_a += (int32_t)a[i] * (int32_t)a[i];
    norm_b += (int32_t)b[i] * (int32_t)b[i];
  }
  return sim_cos_combine(dot, norm_a, norm_b);
}
#endif  // __x86_64__

// --- Function pointers, initialised at load time ---

typedef int32_t (*i8_fn)  (const int8_t*, const int8_t*, size_t);
typedef float   (*cos_i8_fn)(const int8_t*, const int8_t*, size_t);

static i8_fn    dot_i8_impl;
static i8_fn    l2_i8_impl;
static cos_i8_fn cos_i8_impl;

__attribute__((constructor))
static void sim_init(void) {
#if defined(__x86_64__)
  int vnni = has_avx512vnni();
  dot_i8_impl = vnni ? sim_dot_i8_avx512vnni : sim_dot_i8_scalar;
  l2_i8_impl  = vnni ? sim_l2_i8_avx512vnni  : sim_l2_i8_scalar;
  cos_i8_impl = vnni ? sim_cos_i8_avx512vnni  : sim_cos_i8_scalar;
#elif defined(__aarch64__)
#if defined(__ARM_FEATURE_SVE)
  if (has_sve()) {
    dot_i8_impl = sim_dot_i8_sve;
    l2_i8_impl  = sim_l2_i8_sve;
    cos_i8_impl = sim_cos_i8_sve;
  } else
#endif
#if defined(__ARM_NEON) && defined(__ARM_FEATURE_DOTPROD)
  if (has_dotprod()) {
    dot_i8_impl = sim_dot_i8_dotprod;
    l2_i8_impl  = sim_l2_i8_dotprod;
    cos_i8_impl = sim_cos_i8_dotprod;
  } else
#endif
  {
    dot_i8_impl = sim_dot_i8_scalar;
    l2_i8_impl  = sim_l2_i8_scalar;
    cos_i8_impl = sim_cos_i8_scalar;
  }
#else
  dot_i8_impl = sim_dot_i8_scalar;
  l2_i8_impl  = sim_l2_i8_scalar;
  cos_i8_impl = sim_cos_i8_scalar;
#endif
}

// --- Exported functions ---

/// Compute the dot product of two int8 vectors of length n.
EXPORT int32_t mongot_vecsim_dot_i8(const int8_t* a, const int8_t* b, size_t n) {
  return dot_i8_impl(a, b, n);
}

/// Compute the squared L2 distance of two int8 vectors of length n.
EXPORT int32_t mongot_vecsim_l2_i8(const int8_t* a, const int8_t* b, size_t n) {
  return l2_i8_impl(a, b, n);
}

/// Compute the cosine similarity of two int8 vectors of length n.
EXPORT float mongot_vecsim_cos_i8(const int8_t* a, const int8_t* b, size_t n) {
  return cos_i8_impl(a, b, n);
}

/// Return true if all i8 similarity functions are using an accelerated (non-scalar) implementation.
EXPORT bool mongot_vecsim_i8_accelerated() {
  return dot_i8_impl != sim_dot_i8_scalar &&
         l2_i8_impl  != sim_l2_i8_scalar  &&
         cos_i8_impl != sim_cos_i8_scalar;
}
