// Copyright 2023 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { importProvidersFrom } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatListModule } from '@angular/material/list';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatToolbarModule } from '@angular/material/toolbar';
import { BrowserModule, bootstrapApplication } from '@angular/platform-browser';
import { provideAnimations } from '@angular/platform-browser/animations';

import { AppComponent } from './app/app.component';
import { AppRoutingModule } from './app/app-routing.module';
import { unauthorizedInterceptor } from './app/unauthorized.interceptor';

bootstrapApplication(AppComponent, {
    providers: [
        importProvidersFrom(
            AppRoutingModule,
            BrowserModule,
            MatButtonModule,
            MatIconModule,
            MatListModule,
            MatSidenavModule,
            MatToolbarModule,
        ),
        provideAnimations(),
        provideHttpClient(withInterceptors([unauthorizedInterceptor]))
    ]
})
    .catch(err => console.error(err));
