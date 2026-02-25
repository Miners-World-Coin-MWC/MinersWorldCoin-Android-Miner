/*-
 * Copyright 2014 Alexander Peslyak
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted.
 *
 * THIS SOFTWARE IS PROVIDED BY THE AUTHOR AND CONTRIBUTORS ``AS IS'' AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED.
 */

#include "yespower.h"
#include "yespower.c"

#include <stdbool.h>
#include <stdint.h>
#include <string.h>


struct work_restart {
    volatile unsigned long restart;
    char padding[128 - sizeof(unsigned long)];
};

extern struct work_restart *work_restart;
extern bool fulltest(const uint32_t *hash, const uint32_t *target);

/* quick pretest before full 256-bit comparison */
static int pretest(const uint32_t *hash, const uint32_t *target)
{
    return hash[7] < target[7];
}

/* =========================================================
   MWC YESPOWER 1.0 v2 HASH FUNCTION
   MUST MATCH BLOCKCHAIN CONSENSUS PARAMS EXACTLY
   ========================================================= */

void yespowermwc_hash(const char *input, char *output, uint32_t len)
{
    static yespower_params_t params = {
        .version = YESPOWER_1_0,
        .N = 2048,
        .r = 32,
        .pers = (const uint8_t *)
            "Mining made easy and accessible to all - Miners World Coin 2025",
        .perslen = 63
    };

    yespower_tls(
        (const yespower_binary_t *)input,
        len,
        &params,
        (yespower_binary_t *)output
    );
}

/* =========================================================
   SCANHASH FUNCTION FOR MWC
   ========================================================= */

int scanhash_yespowermwc(int thr_id,
                         uint32_t *pdata,
                         const uint32_t *ptarget,
                         uint32_t max_nonce,
                         unsigned long *hashes_done)
{
    uint32_t data[20] __attribute__((aligned(128)));
    uint32_t hash[8]  __attribute__((aligned(32)));

    uint32_t n = pdata[19] - 1;
    const uint32_t first_nonce = pdata[19];

    /* Convert block header to big-endian */
    for (int i = 0; i < 20; i++) {
        be32enc(&data[i], pdata[i]);
    }

    do {
        be32enc(&data[19], ++n);

        yespowermwc_hash((char *)data, (char *)hash, 80);

        if (pretest(hash, ptarget) && fulltest(hash, ptarget)) {
            pdata[19] = n;
            *hashes_done = n - first_nonce + 1;
            return 1;
        }

    } while (n < max_nonce && !work_restart[thr_id].restart);

    *hashes_done = n - first_nonce + 1;
    pdata[19] = n;

    return 0;
}